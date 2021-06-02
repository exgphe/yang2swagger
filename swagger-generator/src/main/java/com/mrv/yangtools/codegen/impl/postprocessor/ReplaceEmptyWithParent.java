/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */
package com.mrv.yangtools.codegen.impl.postprocessor;

import com.mrv.yangtools.common.Tuple;
import io.swagger.models.ComposedModel;
import io.swagger.models.Model;
import io.swagger.models.RefModel;
import io.swagger.models.Swagger;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Replace empty definitions with definition parent
 *
 * @author bartosz.michalik@amartus.com
 */
public class ReplaceEmptyWithParent extends ReplaceDefinitionsProcessor {

    @Override
    protected Map<String, String> prepareForReplacement(OpenAPI openAPI) {
        return openAPI.getComponents().getSchemas().entrySet()
                .stream().filter(e -> {
                    Schema model = e.getValue();
                    if (model instanceof ComposedSchema) {
                        List<Schema> allOf = ((ComposedSchema) model).getAllOf();
                        return allOf.size() == 1 && allOf.get(0).get$ref() != null;
                    }
                    return false;
                }).map(e -> {
                    Schema ref = ((ComposedSchema) e.getValue()).getAllOf().get(0);

                    return new Tuple<>(e.getKey(), ref.get$ref());

                }).collect(Collectors.toMap(Tuple::first, Tuple::second));
    }
}
