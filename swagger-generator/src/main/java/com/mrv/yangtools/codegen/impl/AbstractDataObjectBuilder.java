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
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.Swagger;
import io.swagger.models.properties.AbstractProperty;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.Property;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.*;
import org.opendaylight.yangtools.yang.model.api.type.EnumTypeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.mrv.yangtools.common.BindingMapping.getClassName;

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
    private final ModuleUtils moduleUtils;
    protected final Map<SchemaNode, String> names;
    private final HashMap<QName, String> generatedEnums;

    public AbstractDataObjectBuilder(SchemaContext ctx, Swagger swagger, TypeConverter converter) {
        names = new HashMap<>();
        this.converter = converter;
        converter.setDataObjectBuilder(this);
        this.swagger = swagger;
        this.ctx = ctx;
        this.moduleUtils = new ModuleUtils(ctx);
        generatedEnums = new HashMap<>();
    }

    /**
     * Get definition id for node. Prerequisite is to have node's module traversed {@link UnpackingDataObjectsBuilder#processModule(Module)}.
     * @param node node
     * @return id
     */
    @Override
    public <T extends SchemaNode & DataNodeContainer> String getDefinitionId(T node) {
        return DEF_PREFIX + getName(node);
    }

    /**
     * Traverse model to collect all verbs from YANG nodes that will constitute Swagger models
     * @param module to traverse
     */
    @Override
    public void processModule(Module module) {
        HashSet<String> cache = new HashSet<>(names.values());
        log.debug("processing data nodes defined in {}", module.getName());
        processNode(module, cache);

        log.debug("processing rpcs defined in {}", module.getName());
        module.getRpcs().forEach(r -> {
            processNode(r.getInput(), r.getQName().getLocalName() + "-input",  cache);
            processNode(r.getOutput(),r.getQName().getLocalName() + "-output", cache);
        });
        log.debug("processing augmentations defined in {}", module.getName());
        module.getAugmentations().forEach(r -> processNode(r, cache));
    }

    protected  void processNode(ContainerSchemaNode container, String proposedName, Set<String> cache) {

        String name = generateName(container, proposedName, cache);
        names.put(container, name);

        processNode(container, cache);
    }

    protected void processNode(DataNodeContainer container, Set<String> cache) {
        DataNodeHelper.stream(container).filter(n -> n instanceof ContainerSchemaNode || n instanceof ListSchemaNode)
                .filter(n -> ! names.containsKey(n))
                .forEach(n -> {
                    String name = generateName(n, null, cache);
                    names.put(n, name);
                });
    }

    protected String generateName(SchemaNode node, String proposedName, Set<String> cache) {

        String name = proposedName != null ? getClassName(proposedName) : getClassName(node.getQName());
        if(cache.contains(name)) {

            final Iterable<QName> path = node.getPath().getParent().getPathTowardsRoot();

            for(QName p : path) {
                name = getClassName(p) + name;
                if(! cache.contains(name)) break;
            }

            //TODO if still we have a problem add module name !!!
        }
        return name;
    }

    /**
     * Convert leaf-list to swagger property
     * @param llN leaf-list
     * @return property
     */
    protected Property getPropertyByType(LeafListSchemaNode llN) {
        return converter.convert(llN.getType(), llN);
    }

    /**
     * Convert leaf to swagger property
     * @param lN leaf
     * @return property
     */
    protected Property getPropertyByType(LeafSchemaNode lN) {

        final Property property = converter.convert(lN.getType(), lN);
        property.setDefault(lN.getDefault());

        return property;
    }


    protected <T extends SchemaNode & DataNodeContainer> Map<String, Property> structure(T node) {
        return structure(node,  x -> true, x -> true);
    }


    protected <T extends SchemaNode & DataNodeContainer> Map<String, Property> structure(T node, Predicate<DataSchemaNode> acceptNode, Predicate<DataSchemaNode> acceptChoice) {

        Predicate<DataSchemaNode> choiceP = c -> c instanceof ChoiceSchemaNode;

        // due to how inheritance is handled in yangtools the localName node collisions might appear
        // thus we need to apply collision strategy to override with the last attribute available
        Map<String, Property> properties = node.getChildNodes().stream()
                .filter(choiceP.negate().and(acceptNode)) // choices handled elsewhere
                .map(this::prop).collect(Collectors.toMap(pair -> pair.name, pair -> pair.property, (oldV, newV) -> newV));

        Map<String, Property> choiceProperties = node.getChildNodes().stream()
                .filter(choiceP.and(acceptChoice)) // handling choices
                .flatMap(c -> {
                    ChoiceSchemaNode choice = (ChoiceSchemaNode) c;
                    return choice.getCases().stream()
                            .flatMap(_case -> _case.getChildNodes().stream().map(sc -> {
                                Pair prop = prop(sc);
                                assignCaseMetadata(prop.property, choice, _case);
                                return prop;
                            }));
                }).collect(Collectors.toMap(pair -> pair.name, pair -> pair.property, (oldV, newV) -> newV));

        HashMap<String, Property> result = new HashMap<>();

        result.putAll(properties);
        result.putAll(choiceProperties);
        return result;
    }

    protected Pair prop(DataSchemaNode node) {
        final String propertyName = getPropertyName(node);

        Property prop = null;

        if (node instanceof LeafListSchemaNode) {
            LeafListSchemaNode ll = (LeafListSchemaNode) node;
            prop = new ArrayProperty(getPropertyByType(ll));
        } else if (node instanceof LeafSchemaNode) {
            LeafSchemaNode lN = (LeafSchemaNode) node;
            prop = getPropertyByType(lN);
        } else if (node instanceof ContainerSchemaNode) {
            prop = refOrStructure((ContainerSchemaNode) node);
        } else if (node instanceof ListSchemaNode) {
            prop = new ArrayProperty().items(refOrStructure((ListSchemaNode) node));
        }

        if (prop != null) {
            prop.setDescription(desc(node));
        }

        return new Pair(propertyName, prop);
    }

    public String getPropertyName(DataSchemaNode node) {
        //return BindingMapping.getPropertyName(node.getQName().getLocalName());
        String name = node.getQName().getLocalName();
        if(node.isAugmenting()) {
            name = moduleName(node) + ":" + name;
        }
        return name;
    }

    private String moduleName(DataSchemaNode node) {
        Module module = ctx.findModuleByNamespaceAndRevision(node.getQName().getNamespace(), node.getQName().getRevision());
        return module.getName();
    }

    protected abstract <T extends DataSchemaNode & DataNodeContainer> Property refOrStructure(T node);

    private static void assignCaseMetadata(Property property, ChoiceSchemaNode choice, ChoiceCaseNode aCase) {
        String choiceName = choice.getQName().getLocalName();
        String caseName = aCase.getQName().getLocalName();

        ((AbstractProperty) property).setVendorExtension("x-choice", choiceName + ":" + caseName);
    }

    /**
     * Add model to referenced swagger for given node. All related models are added as well if needed.
     * @param node for which build a node
     * @param <T> type of the node
     */
    @Override
    public <T extends SchemaNode & DataNodeContainer> void addModel(T node) {
        Model model = build(node);

        String modelName = getName(node);
        if(swagger.getDefinitions() != null && swagger.getDefinitions().containsKey(modelName)) {
            if(swagger.getDefinitions().get(modelName) == model) {
                return;
            }
            log.warn("Overriding model {} with node {}", modelName, node.getQName());
        }

        swagger.addDefinition(modelName, model);
    }

    @Override
    public String addModel(EnumTypeDefinition enumType) {
        QName qName = enumType.getQName();

        if(! generatedEnums.containsKey(qName)) {
            log.debug("generating enum model for {}", enumType.getQName());
            String name = getName(qName);
            ModelImpl enumModel = build(enumType);
            swagger.addDefinition(name, enumModel);
            generatedEnums.put(qName, DEF_PREFIX + name);
        } else {
            log.debug("reusing enum model for {}", enumType.getQName());
        }
        return generatedEnums.get(qName);
    }

    protected ModelImpl build(EnumTypeDefinition enumType) {
        ModelImpl model = new ModelImpl();
        model._enum(enumType.getValues().stream()
                .map(EnumTypeDefinition.EnumPair::getName).collect(Collectors.toList()));
        model.setType(ModelImpl.OBJECT);
        return model;
    }

    protected String getName(QName qname) {
        String name = getClassName(qname);
        if (generatedEnums.values().contains(name)) {
            name = moduleUtils.toModuleName(qname) + name;
        }
        while(generatedEnums.values().contains(name)) {
            log.warn("Name {} already defined for enum. generating random postfix");
            name = name + new Random().nextInt();
        }
        return name;
    }

    protected String desc(DocumentedNode node) {
        return  node.getReference() == null ? node.getDescription() :
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