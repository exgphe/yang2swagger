package com.mrv.yangtools.codegen.impl.postprocessor;

import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Wrap payload with a single rooted, works with {@link com.mrv.yangtools.codegen.impl.path.rfc8040.RestconfPathPrinter}
 * path schema only
 *
 * @author bartosz.michalik@amartus.com
 */
public abstract class PayloadWrapperProcessor implements Consumer<Swagger> {
    private static final Logger log = LoggerFactory.getLogger(PayloadWrapperProcessor.class);
    private static final String POSTFIX = "Wrapper";
    private Swagger swagger;

    @Override
    public void accept(Swagger swagger) {
        this.swagger = Objects.requireNonNull(swagger);
        this.swagger.getPaths().forEach(this::processPath);
    }

    private void processPath(String key, Path path) {
        if (key.startsWith("/operations")) {
            processOperation(path.getPost(), toProperty(key), true);
            return;
        }

        processOperation(path.getGet(), toProperty(key));
        processOperation(path.getPut(), toProperty(key));
        processOperation(path.getPost(), toProperty(key), true);
        processOperation(path.getPatch(), toProperty(key));
        processOperation(path.getDelete(), toProperty(key));
    }

    protected abstract String toProperty(String path);

    private void processOperation(Operation operation, String propertyName) {
        processOperation(operation, propertyName, false);
    }

    private void processOperation(Operation operation, String propertyName, Boolean isPost) {
        if (operation == null) {
            return;
        }

        operation.getResponses().values().stream()
                .filter(r -> r.getSchema() instanceof RefProperty)
                .forEach(r -> wrap(propertyName, r, operation, isPost));

        operation.getParameters().stream()
                .filter(p -> p instanceof BodyParameter)
                .map(p -> (BodyParameter) p)
                .filter(p -> p.getSchema() instanceof RefModel)
                .forEach(param -> wrap(propertyName, param, operation, isPost));


    }

    private void wrap(String propertyName, Response r, Operation operation, Boolean isRpcOutput) {
        RefProperty prop = (RefProperty) r.getSchema();
        String wrapperName;
        if (isRpcOutput) {
            wrapperName = wrapPostBodyParameter(prop.getSimpleRef(), operation.getTags().get(0));
//            swagger.getDefinitions().remove(prop.getSimpleRef());
        } else {
            wrapperName = wrap(propertyName, prop.getSimpleRef(), operation.getTags().get(0));
        }
        r.setSchema(new RefProperty(wrapperName));
    }

    private void wrap(String propertyName, BodyParameter param, Operation operation, Boolean isPost) {
        RefModel m = (RefModel) param.getSchema();
        String wrapperName;
        if (isPost) {
            wrapperName = wrapPostBodyParameter(m.getSimpleRef(), operation.getTags().get(0));
        } else {
            wrapperName = wrap(propertyName, m.getSimpleRef(), operation.getTags().get(0));
        }
        param.setSchema(new RefModel(wrapperName));
    }

    private String wrap(String propertyName, String simpleRef, String moduleName) {
        String wrapperName = simpleRef + POSTFIX;
        Model model = swagger.getDefinitions().get(wrapperName);
        if (model == null) {
            addWrappingModel(wrapperName, propertyName, simpleRef);
        }
        model = swagger.getDefinitions().get(wrapperName);
        Set<String> keySet = model.getProperties().keySet();
        for (String key : keySet) {
            if (!key.contains(":")) {
                Property property = model.getProperties().get(key);
                model.getProperties().put(moduleName + ":" + key, property);
                model.getProperties().remove(key);
            }
        }
        return wrapperName;
    }

    private String wrapPostBodyParameter(String simpleRef, String moduleName) {
        // Add namespace prefixes to all parameters
        String wrapperName = simpleRef + POSTFIX + "_post";
        ModelImpl originalModel = (ModelImpl) swagger.getDefinitions().get(simpleRef);
        ModelImpl postModel = new ModelImpl();
        originalModel.cloneTo(postModel);
        postModel.setType(originalModel.getType());
        postModel.setName(originalModel.getName());
        postModel.setSimple(originalModel.isSimple());
        postModel.setDescription(originalModel.getDescription());
        postModel.setExample(originalModel.getExample());
        postModel.setAdditionalProperties(originalModel.getAdditionalProperties());
        postModel.setDiscriminator(originalModel.getDiscriminator());
        postModel.setXml(originalModel.getXml());
        postModel.setDefaultValue(originalModel.getDefaultValue());
        if(originalModel.getProperties()!=null) {
            Set<String> keySet = originalModel.getProperties().keySet();
            for (String key : keySet) {
                Property property = originalModel.getProperties().get(key);
                if (!key.contains(":")) {
                    postModel.addProperty(moduleName + ":" + key, property);
                } else {
                    postModel.addProperty(key, property);
                }
            }
        }
        swagger.addDefinition(wrapperName, postModel);
        return wrapperName;
    }

    private void addWrappingModel(String wrapperName, String propertyName, String simpleRef) {
        log.info("Adding top-level model {} {} -> {}", wrapperName, propertyName, simpleRef);
        ModelImpl model = new ModelImpl();
        model.addProperty(propertyName, new RefProperty(simpleRef));
        swagger.addDefinition(wrapperName, model);
    }
}
