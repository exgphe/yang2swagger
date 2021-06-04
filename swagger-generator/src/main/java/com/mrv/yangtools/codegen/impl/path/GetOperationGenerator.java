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

package com.mrv.yangtools.codegen.impl.path;

import com.mrv.yangtools.codegen.DataObjectRepo;
import com.mrv.yangtools.codegen.PathSegment;
import io.swagger.models.Operation;
import io.swagger.models.Response;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.models.properties.IntegerProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class GetOperationGenerator extends OperationGenerator {
    public GetOperationGenerator(PathSegment path, DataObjectRepo repo) {
        super(path, repo);
    }

    @Override
    public Operation execute(DataSchemaNode node) {
        final Operation get = defaultOperation();
        get.summary("returns " + getName(node));
        String description = node.getDescription() == null ? "returns " + getName(node) :
                node.getDescription();
        get.description(description);

//        QueryParameter content = new QueryParameter().name("content").description("Select config and/or non-config data resources").type("string");
//        content.setEnum(new ArrayList<String>() {{
//            add("config");
//            add("nonconfig");
//            add("all");
//        }});
//        content.setDefault("all");
//        get.addParameter(content);
//
//        QueryParameter depth = new QueryParameter().name("depth").description("Request limited subtree depth in the reply content").type("string");
//        depth.setDefault("unbounded");
//        depth.setVendorExtension("x-union", new ArrayList<Property>() {{
//            add(new StringProperty()._enum(Collections.singletonList("unbounded")));
//            add(new IntegerProperty().maximum(65536.).minimum(0.));
//        }});
//        get.addParameter(depth);

        get.response(200, new Response()
                .schema(new RefProperty(getDefinitionId(node)))
                .description(getName(node)));
        get.response(400, new Response().description("Bad Request"));
        get.response(401, new Response().description("Unauthorized"));
        get.response(404, new Response().description("Not Found"));
        return get;
    }

    public static Operation toHead(Operation getOperation) {
        Operation headOperation = new Operation();
        headOperation.setParameters(getOperation.getParameters());
        headOperation.setSummary(getOperation.getSummary());
        headOperation.setDescription(getOperation.getDescription());
        headOperation.setResponses(getOperation.getResponses().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> new Response().description(entry.getValue().getDescription()))));
        headOperation.setTags(getOperation.getTags());
        return headOperation;
    }
}
