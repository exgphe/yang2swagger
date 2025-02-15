/*
 * Copyright (c) 2016 MRV Communications, Inc. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Christopher Murch <cmurch@mrv.com>
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen.impl;

import com.mrv.yangtools.codegen.DataObjectBuilder;
import io.swagger.models.*;
import io.swagger.models.properties.*;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.*;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.type.EnumTypeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.mrv.yangtools.common.BindingMapping.getClassName;
import static com.mrv.yangtools.common.BindingMapping.nameToPackageSegment;

/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public abstract class AbstractDataObjectBuilder implements DataObjectBuilder {

    private static final Logger log = LoggerFactory.getLogger(AbstractDataObjectBuilder.class);

    protected static final String DEF_PREFIX = "#/definitions/";
    protected final Swagger swagger;
    protected final TypeConverter converter;
    protected final SchemaContext ctx;
    protected final ModuleUtils moduleUtils;
    protected final Map<SchemaNode, String> names;
    private final HashMap<QName, String> generatedEnums;
    private final HashMap<DataNodeContainer, String> orgNames;

    protected final static Function<DataNodeContainer, Set<AugmentationSchema>> augmentations = node -> {
        if (node instanceof AugmentationTarget) {
            Set<AugmentationSchema> res = ((AugmentationTarget) node).getAvailableAugmentations();
            if (res != null) return res;
        }
        return Collections.emptySet();
    };

    protected final static Predicate<DataNodeContainer> isAugmented = n -> !augmentations.apply(n).isEmpty();

    protected final Predicate<DataNodeContainer> isTreeAugmented = n -> n != null && (isAugmented.test(n) || n.getChildNodes().stream()
            .filter(c -> c instanceof DataNodeContainer)
            .anyMatch(c -> this.isTreeAugmented.test((DataNodeContainer) c)));

    public AbstractDataObjectBuilder(SchemaContext ctx, Swagger swagger, TypeConverter converter) {
        this.names = new HashMap<>();
        this.converter = converter;
        converter.setDataObjectBuilder(this);
        this.swagger = swagger;
        this.ctx = ctx;
        this.moduleUtils = new ModuleUtils(ctx);
        this.generatedEnums = new HashMap<>();
        this.orgNames = new HashMap<>();
    }

    /**
     * Get definition id for node. Prerequisite is to have node's module traversed {@link UnpackingDataObjectsBuilder#processModule(Module)}.
     *
     * @param node node
     * @return id
     */
    @Override
    public <T extends SchemaNode & DataNodeContainer> String getDefinitionId(T node) {
        return DEF_PREFIX + getName(node);
    }

    /**
     * Traverse model to collect all verbs from YANG nodes that will constitute Swagger models
     *
     * @param module to traverse
     */
    @Override
    public void processModule(Module module) {
        HashSet<String> cache = new HashSet<>(names.values());
        log.debug("processing data nodes defined in {}", module.getName());
        processNode(module, cache);

        log.debug("processing rpcs defined in {}", module.getName());
        module.getRpcs().forEach(r -> {
            if (r.getInput() != null)
                processNode(r.getInput(), null, cache);
            if (r.getOutput() != null)
                processNode(new RpcContainerSchemaNode(r), null, cache);
        });
        log.debug("processing augmentations defined in {}", module.getName());
        module.getAugmentations().forEach(r -> processNode(r, cache));
    }

    protected void processNode(ContainerSchemaNode container, String proposedName, Set<String> cache) {
        if (container == null) return;
        String name = generateName(container, null, cache);
        names.put(container, name);

        processNode(container, cache);
    }

    protected void processNode(DataNodeContainer container, Set<String> cache) {
        log.debug("DataNodeContainer string: {}", container.toString());
        DataNodeHelper.stream(container).filter(n -> n instanceof ContainerSchemaNode || n instanceof ListSchemaNode)
                .filter(n -> !names.containsKey(n))
                .forEach(n -> {
                    String name = generateName(n, null, cache);
                    names.put(n, name);
                });
    }

    protected DataNodeContainer original(DataNodeContainer node) {
        DataNodeContainer result = null;
        DataNodeContainer tmp = node;
        do {
            if (tmp instanceof DerivableSchemaNode) {
                com.google.common.base.Optional<? extends SchemaNode> original = ((DerivableSchemaNode) tmp).getOriginal();
                tmp = null;
                if (original.isPresent() && original.get() instanceof DataNodeContainer) {
                    result = (DataNodeContainer) original.get();
                    tmp = result;
                }
            } else {
                tmp = null;
            }
        } while (tmp != null);

        return result;
    }

    protected String generateName(SchemaNode node, String proposedName, Set<String> _cache) {
        if (node instanceof DataNodeContainer) {
            DataNodeContainer original = null;
            if (!isTreeAugmented.test((DataNodeContainer) node)) {
                original = original((DataNodeContainer) node);
            }

            if (original != null) {
                if (!orgNames.containsKey(original)) {
                    String name = generateName((SchemaNode) original, proposedName, _cache);
                    orgNames.put(original, name);
                } else {
                    log.debug("reusing original definition to get name for {}", node.getQName());
                }

                return orgNames.get(original);
            } else {
                DataNodeContainer t = (DataNodeContainer) node;
                if (orgNames.containsKey(t)) {
                    return orgNames.get(t);
                }
            }
        }

        String modulePrefix = nameToPackageSegment(moduleUtils.toModuleName(node.getQName()));
        if (proposedName != null) {
            return modulePrefix + "." + getClassName(proposedName);
        }

        String name = getClassName(node.getQName());
        final Iterable<QName> path = node.getPath().getParent().getPathFromRoot();
        if (path == null || !path.iterator().hasNext()) {
            log.debug("generatedName: {}", modulePrefix + "." + name);
            return modulePrefix + "." + name;
        }
        String pkg = StreamSupport.stream(path.spliterator(), false).map(n -> getClassName(n.getLocalName()).toLowerCase()).collect(Collectors.joining("."));
        log.debug("generatedName: {}", modulePrefix + "." + pkg + "." + name);
        return modulePrefix + "." + pkg + "." + name;
    }

    /**
     * Convert leaf-list to swagger property
     *
     * @param llN leaf-list
     * @return property
     */
    protected Property getPropertyByType(LeafListSchemaNode llN) {
        return converter.convert(llN.getType(), llN);
    }

    /**
     * Convert leaf to swagger property
     *
     * @param lN leaf
     * @return property
     */
    protected Property getPropertyByType(LeafSchemaNode lN) {

        final Property property = converter.convert(lN.getType(), lN);
        property.setDefault(lN.getDefault());

        return property;
    }

    protected Map<String, Property> structure(DataNodeContainer node) {
        return structure(node, false);
    }

    protected Map<String, Property> structure(DataNodeContainer node, Boolean isRpc) {
        return structure(node, x -> true, x -> true, isRpc);
    }

    protected Map<String, Property> structure(DataNodeContainer node, Predicate<DataSchemaNode> acceptNode, Predicate<DataSchemaNode> acceptChoice) {
        return structure(node, acceptNode, acceptChoice, false);
    }

    protected Map<String, Property> structure(DataNodeContainer node, Predicate<DataSchemaNode> acceptNode, Predicate<DataSchemaNode> acceptChoice, Boolean isRpc) {

        Predicate<DataSchemaNode> choiceP = c -> c instanceof ChoiceSchemaNode;

        // due to how inheritance is handled in yangtools the localName node collisions might appear
        // thus we need to apply collision strategy to override with the last attribute available
        Map<String, Property> properties = node.getChildNodes().stream()
                .filter(choiceP.negate().and(acceptNode)) // choices handled elsewhere
                .map(n -> this.prop(n, isRpc)).collect(Collectors.toMap(pair -> pair.name, pair -> pair.property, (oldV, newV) -> newV));
        HashMap<String, Property> result = new HashMap<>(properties);
        List<ChoiceSchemaNode> choiceSchemaNodes = node.getChildNodes().stream()
                .filter(choiceP.and(acceptChoice)).map(n -> (ChoiceSchemaNode) n).collect(Collectors.toList());
        for (ChoiceSchemaNode choiceSchemaNode : choiceSchemaNodes) {
            result.putAll(handleChoices(choiceSchemaNode, isRpc));
        }
        return result;
    }

    private Map<String, Property> handleChoices(ChoiceSchemaNode choice, Boolean isRpc) {
        Map<String, Property> result = new HashMap<>();
        for (ChoiceCaseNode choiceCase : choice.getCases()) {
            for (DataSchemaNode childNode : choiceCase.getChildNodes()) {
                if (childNode instanceof ChoiceSchemaNode) {
                    Map<String, Property> subProperties = handleChoices((ChoiceSchemaNode) childNode, isRpc);
                    subProperties.forEach((name, property) -> assignCaseMetadata(property, choice, choiceCase));
                    result.putAll(subProperties);
                } else {
                    Pair prop = prop(childNode, isRpc);
                    assignCaseMetadata(prop.property, choice, choiceCase);
                    result.put(prop.name, prop.property);
                }
            }
        }
        return result;
    }

    protected Pair prop(DataSchemaNode node) {
        return prop(node, false);
    }

    protected Pair prop(DataSchemaNode node, Boolean isRpc) {
        final String propertyName = getPropertyName(node);

        Property prop = null;

        if (node instanceof LeafListSchemaNode) {
            LeafListSchemaNode ll = (LeafListSchemaNode) node;
            prop = new ArrayProperty(getPropertyByType(ll));
            ArrayProperty arrayProp = ((ArrayProperty) prop);
            if (node.getConstraints() != null) {
                if (node.getConstraints().getMaxElements() != null) {
                    arrayProp.setMaxItems(node.getConstraints().getMaxElements());
                }
                if (node.getConstraints().getMinElements() != null) {
                    arrayProp.setMinItems(node.getConstraints().getMinElements());
                }
            }
        } else if (node instanceof LeafSchemaNode) {
            LeafSchemaNode lN = (LeafSchemaNode) node;
            prop = getPropertyByType(lN);
        } else if (node instanceof ContainerSchemaNode) {
            prop = refOrStructure((ContainerSchemaNode) node, isRpc);
        } else if (node instanceof ListSchemaNode) {
            ListSchemaNode ls = (ListSchemaNode) node;
            if (isRpc) {
                prop = new ArrayProperty().items(refOrStructure(ls, isRpc));
                ArrayProperty arrayProp = ((ArrayProperty) prop);
                if (!ls.getKeyDefinition().isEmpty()) {
                    Stream<String> keys = ls.getKeyDefinition().stream().map(QName::getLocalName);
                    arrayProp.setVendorExtension("x-key", keys.collect(Collectors.joining(",")));
                }
//            Property itemsProperty = arrayProp.getItems();
//            if(itemsProperty instanceof RefProperty) {
//                Model itemsStructureProperty = swagger.getDefinitions().get(((RefProperty) itemsProperty).getSimpleRef());
//                keys.forEach(key -> itemsStructureProperty.getProperties().get(key).setRequired(true));
//            } else {
//                ObjectProperty itemsStructureProperty = (ObjectProperty) itemsProperty;
//                keys.forEach(key -> itemsStructureProperty.getProperties().get(key).setRequired(true));
//            }
                if (node.getConstraints() != null) {
                    if (node.getConstraints().getMaxElements() != null) {
                        arrayProp.setMaxItems(node.getConstraints().getMaxElements());
                    }
                    if (node.getConstraints().getMinElements() != null) {
                        arrayProp.setMinItems(node.getConstraints().getMinElements());
                    }
                }
            } else {
                prop = refOrStructure(ls, isRpc);
            }
        } else if (node instanceof AnyXmlSchemaNode) {
            log.warn("generating swagger string property for any schema type for {}", node.getQName());
            prop = new AbstractProperty() {
            };
            ((AbstractProperty) prop).setVendorExtension("x-anyxml", true);

        } else if (node instanceof AnyDataSchemaNode) {
//            if(((AnyDataSchemaNode) node).getSchemaOfAnyData()!=null) {
//                prop = refOrStructure(((AnyDataSchemaNode) node).getSchemaOfAnyData());
//            } else {
            prop = new ObjectProperty();
            ((AbstractProperty) prop).setVendorExtension("x-anydata", true);
//            }
        }

        if (prop != null) {
            prop.setDescription(desc(node));
            if (node.getConstraints().isMandatory()) {
                prop.getVendorExtensions().put("x-mandatory", true);
            }
//            prop.setRequired(node.getConstraints().isMandatory()); // TODO in PATCH and some POST it is not required
            if (!node.isConfiguration()) {
                prop.setReadOnly(true);
            }
        }
        return new Pair(propertyName, prop);
    }

    public String getPropertyName(DataSchemaNode node) {
        //return BindingMapping.getPropertyName(node.getQName().getLocalName());
        String name = node.getQName().getLocalName();
        if (node.isAugmenting()) {
            String moduleName = moduleName(node);
            QName parentQName = node.getPath().getParent().getLastComponent();
            if (!ctx.findModuleByNamespaceAndRevision(parentQName.getNamespace(), parentQName.getRevision()).getName().equals(moduleName)) {
                name = moduleName + ":" + name;
            }
        }
        return name;
    }

    private String moduleName(DataSchemaNode node) {
        Module module = ctx.findModuleByNamespaceAndRevision(node.getQName().getNamespace(), node.getQName().getRevision());
        return module.getName();
    }

    protected abstract <T extends DataSchemaNode & DataNodeContainer> Property refOrStructure(T node, Boolean isRpc);

    private static void assignCaseMetadata(Property property, ChoiceSchemaNode choice, ChoiceCaseNode aCase) {
        String choiceName = choice.getQName().getLocalName();
        String caseName = aCase.getQName().getLocalName();
//        ((AbstractProperty) property).setVendorExtension("x-choice", choiceName + ":" + caseName);
        if (!property.getVendorExtensions().containsKey("x-choice")) {
            property.getVendorExtensions().put("x-choice", new ArrayList<String>());
        }
        ((List<String>) property.getVendorExtensions().get("x-choice")).add(choiceName + ":" + caseName);
    }

    /**
     * Add model to referenced swagger for given node. All related models are added as well if needed.
     *
     * @param <T>       type of the node
     * @param node      for which build a node
     * @param modelName model name
     * @param isRpc
     */
    @Override
    public <T extends SchemaNode & DataNodeContainer> void addModel(T node, String modelName, Boolean isRpc) {


        Model model = build(node, isRpc);


        if (swagger.getDefinitions() != null && swagger.getDefinitions().containsKey(modelName)) {
            if (model.equals(swagger.getDefinitions().get(modelName))) {
                return;
            }
            log.warn("Overriding model {} with node {}", modelName, node.getQName());
//            swagger.addDefinition(modelName + UUID.randomUUID(), swagger.getDefinitions().get(modelName));
        }

        swagger.addDefinition(modelName, model);
    }

    public <T extends SchemaNode & DataNodeContainer> void addModel(T node, Boolean isRpc) {
        addModel(node, getName(node), isRpc);
    }


    @Override
    public String addModel(EnumTypeDefinition enumType) {
        QName qName = enumType.getQName();

        //inline enumerations are a special case that needs extra enumeration
        if (qName.getLocalName().equals("enumeration") && enumType.getBaseType() == null) {
            qName = QName.create(qName, enumType.getPath().getParent().getLastComponent().getLocalName() + "-" + qName.getLocalName());
        }

        if (!generatedEnums.containsKey(qName)) {
            log.debug("generating enum model for {}", qName);
            String name = getName(qName);
            ModelImpl enumModel = build(enumType, qName);
            swagger.addDefinition(name, enumModel);
            generatedEnums.put(qName, DEF_PREFIX + name);
        } else {
            log.debug("reusing enum model for {}", enumType.getQName());
        }
        return generatedEnums.get(qName);
    }

    protected ModelImpl build(EnumTypeDefinition enumType, QName qName) {
        ModelImpl model = new ModelImpl();
        model.setEnum(enumType.getValues().stream()
                .map(EnumTypeDefinition.EnumPair::getName).collect(Collectors.toList()));
        model.setType("string");
        model.setDescription(enumType.getDescription()); // TODO each enum value has description too
        if (enumType.getDefaultValue() != null) model.setDefaultValue(enumType.getDefaultValue().toString());
        model.setReference(getName(qName));
        return model;
    }

    protected String getName(QName qname) {
        String modulePrefix = nameToPackageSegment(moduleUtils.toModuleName(qname));
        String name = modulePrefix + "." + getClassName(qname);

        String candidate = name;

        int idx = 1;
        while (generatedEnums.values().contains(DEF_PREFIX + candidate)) {
            log.warn("Name {} already defined for enum. generating random postfix", candidate);
            candidate = name + idx;
        }
        return candidate;
    }

    protected String desc(DocumentedNode node) {
        return node.getReference() == null ? node.getDescription() :
                node.getDescription() + " REF:" + node.getReference();
    }

    protected static class Pair {
        final protected String name;
        final protected Property property;

        protected Pair(String name, Property property) {
            this.name = name;
            this.property = property;
        }
    }
}
