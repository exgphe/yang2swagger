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

import io.swagger.models.*;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Processor that allows replacing one definitions with another in any swagger.
 * This implementation is simple and limited only to the type definitions that are aggregators of references.
 * @author bartosz.michalik@amartus.com
 */
public abstract class ReplaceDefinitionsProcessor implements Consumer<OpenAPI> {
    private final Logger log = LoggerFactory.getLogger(ReplaceDefinitionsProcessor.class);
    @Override
    public void accept(OpenAPI target) {
        Map<String, String> replacements = prepareForReplacement(target);

        log.debug("{} replacement found for definitions", replacements.size());
        log.trace("replacing paths");
        target.getPaths().values().stream().flatMap(p -> p.readOperations().stream())
                .forEach(o -> fixOperation(o, replacements));

        target.getComponents().getSchemas().forEach((key, value) -> fixModel(key, value, replacements));
        replacements.keySet().forEach(r -> {
            log.debug("removing {} model from swagger definitions", r);
            target.getComponents().getSchemas().remove(r);
        });
    }

    protected abstract Map<String, String> prepareForReplacement(OpenAPI openAPI);

    private void fixModel(String name, Schema m, Map<String, String> replacements) {
        Schema fixProperties = null;
        if(m instanceof Schema) {
            fixProperties = (Schema) m;
        }

        if(m instanceof ComposedSchema) {
            ComposedSchema cm = (ComposedSchema) m;
            fixComposedModel(name, cm, replacements);
            fixProperties =  cm.getAllOf().stream()
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);
        }

        if(fixProperties == null) return;
        if(fixProperties.getProperties() == null) {
            if(fixProperties.getEnum() == null) {
                log.warn("Empty model in {}", name);
            }
            return;
        }
        fixProperties.getProperties().forEach((key, value) -> {
            Schema schema = (Schema) value;
            if (value instanceof ArraySchema) {
                Schema items = ((ArraySchema) value).getItems();
                if (items.get$ref()!=null) {
                    if (fixProperty(items, replacements)) {
                        log.debug("fixing property {} of {}", key, name);
                    }
                }
            } else if (schema.get$ref()!=null) {
                if (fixProperty(schema, replacements)) {
                    log.debug("fixing property {} of {}", key, name);
                }
            }
        });

    }
    private boolean fixProperty(Schema p, Map<String, String> replacements) {
        if(replacements.containsKey(p.get$ref())) {
            p.set$ref(replacements.get(p.get$ref()));
            return true;
        }
        return false;
    }

    private void fixComposedModel(String name, ComposedSchema m, Map<String, String> replacements) {
        Set<Schema> toReplace = m.getAllOf().stream().filter(c -> c.get$ref()!=null)
                .filter(rm -> replacements.containsKey(rm.get$ref())).collect(Collectors.toSet());
        toReplace.forEach(r -> {
            int idx = m.getAllOf().indexOf(r);
            Schema newRef = new Schema().$ref(replacements.get(r.get$ref()));
            m.getAllOf().set(idx, newRef);
            if(m.getAllOf().remove(r)) {
                m.getAllOf().add(newRef);
            }
        });
    }


    private void fixOperation(Operation operation, Map<String, String> replacements) {
        operation.getResponses().values()
                .forEach(r -> fixResponse(r, replacements));
        operation.getParameters().forEach(p -> fixParameter(p, replacements));
        Optional<Map.Entry<String, String>> rep = replacements.entrySet().stream()
                .filter(r -> operation.getDescription() != null && operation.getDescription().contains(r.getKey()))
                .findFirst();
        if(rep.isPresent()) {
            log.debug("fixing description for '{}'", rep.get().getKey());
            Map.Entry<String, String> entry = rep.get();
            operation.setDescription(operation.getDescription().replace(entry.getKey(), entry.getValue()));
        }

    }

    private void fixParameter(Parameter p, Map<String, String> replacements) {
        if(!(p instanceof BodyParameter)) return;
        BodyParameter bp = (BodyParameter) p;
        if(!(bp.getSchema() instanceof RefModel)) return;
        RefModel ref = (RefModel) bp.getSchema();
        if(replacements.containsKey(ref.getSimpleRef())) {
            String replacement = replacements.get(ref.getSimpleRef());
            bp.setDescription(bp.getDescription().replace(ref.getSimpleRef(), replacement));
            bp.setSchema(new RefModel(replacement));
        }

    }

    private void fixResponse(ApiResponse r, Map<String, String> replacements) {
        if(! (r.getSchema() instanceof RefProperty)) return;
        RefProperty schema = (RefProperty) r.getSchema();
        if(replacements.containsKey(schema.getSimpleRef())) {
            String replacement = replacements.get(schema.getSimpleRef());
            if(r.getDescription() != null)
                r.setDescription(r.getDescription().replace(schema.getSimpleRef(), replacement));
            schema.setDescription(replacement);
            r.setSchema(new RefProperty(replacement));
        }

    }
}
