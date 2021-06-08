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

import com.mrv.yangtools.codegen.impl.swagger.ArrayModelImpl;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.Swagger;
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Used to convert YANG data nodes to Swagger models. The generator strategy is to unpack
 * all groupings attributes into container that use them.
 *
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class UnpackingDataObjectsBuilder extends AbstractDataObjectBuilder {

    private static final Logger log = LoggerFactory.getLogger(UnpackingDataObjectsBuilder.class);

    private Set<String> built;

    /**
     * @param ctx     YANG modules context
     * @param swagger for which models are built
     */
    public UnpackingDataObjectsBuilder(SchemaContext ctx, Swagger swagger, TypeConverter converter) {
        super(ctx, swagger, converter);
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(swagger);
        built = new HashSet<>();
    }

    /**
     * Build Swagger model for given Yang data node
     *
     * @param node for which we want to build model
     * @param <T>  YANG node type
     * @return Swagger model
     */
    public <T extends SchemaNode & DataNodeContainer> Model build(T node) {
        ModelImpl model;
        Map<String, Property> properties = structure(node);
        String nodeName = getName(node);
        if (node instanceof ListSchemaNode) {
            ListSchemaNode listSchemaNode = (ListSchemaNode) node;
            model = new ArrayModelImpl();
            ArrayModelImpl arrayModel = (ArrayModelImpl) model;
            ModelImpl itemsModel = new ModelImpl();
            itemsModel.setType("object");
            itemsModel.setProperties(properties);
            RefProperty itemsRef = new RefProperty();
            itemsRef.set$ref(nodeName + "Item");
            swagger.getDefinitions().put(itemsRef.getSimpleRef(), itemsModel);
            arrayModel.setItems(itemsRef);
            Stream<String> keys = listSchemaNode.getKeyDefinition().stream().map(QName::getLocalName);
            arrayModel.setVendorExtension("x-key", keys.collect(Collectors.joining(",")));
            //            if(itemsProperty instanceof RefProperty) {
//                Model itemsStructureProperty = swagger.getDefinitions().get(((RefProperty) itemsProperty).getSimpleRef());
//                keys.forEach(key -> itemsStructureProperty.getProperties().get(key).setRequired(true));
//            } else {
//                ObjectProperty itemsStructureProperty = (ObjectProperty) itemsProperty;
//                keys.forEach(key -> itemsStructureProperty.getProperties().get(key).setRequired(true));
//            }
            if (listSchemaNode.getConstraints() != null) {
                if (listSchemaNode.getConstraints().getMaxElements() != null) {
                    arrayModel.setMaxItems(listSchemaNode.getConstraints().getMaxElements());
                }
                if (listSchemaNode.getConstraints().getMinElements() != null) {
                    arrayModel.setMinItems(listSchemaNode.getConstraints().getMinElements());
                }
            }
        } else {
            model = new ModelImpl();
            model.setProperties(properties);
        }
        model.description(desc(node));

        built.add(nodeName);

        return model;
    }

    /**
     * Get name for data node. Prerequisite is to have node's module traversed {@link UnpackingDataObjectsBuilder#processModule(Module)}.
     *
     * @param node node
     * @return name
     */
    @Override
    public <T extends SchemaNode & DataNodeContainer> String getName(T node) {
        return names.get(node);
    }

    protected <T extends DataSchemaNode & DataNodeContainer> Property refOrStructure(T node) {
        final boolean useReference = built.contains(getName(node));
        Property prop;
        if (useReference) {
            final String definitionId = getDefinitionId(node);
            log.debug("reference to {}", definitionId);
            prop = new RefProperty(definitionId);
        } else {
            log.debug("submodel for {}", getName(node));
            prop = new ObjectProperty(structure(node, x -> true, x -> true));
        }
        return prop;
    }

}
