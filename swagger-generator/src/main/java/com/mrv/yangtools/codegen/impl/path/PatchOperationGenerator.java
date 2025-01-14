package com.mrv.yangtools.codegen.impl.path;

import com.mrv.yangtools.codegen.DataObjectRepo;
import com.mrv.yangtools.codegen.PathSegment;
import io.swagger.models.Operation;
import io.swagger.models.RefModel;
import io.swagger.models.Response;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.properties.RefProperty;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;

/**
 * @author bartosz.michalik@amartus.com
 */
public class PatchOperationGenerator extends OperationGenerator {
    public PatchOperationGenerator(PathSegment path, DataObjectRepo repo) {
        super(path, repo);
    }

    @Override
    public Operation execute(DataSchemaNode node) {
        final Operation patch = defaultOperation();
        final RefModel definition = new RefModel(getDefinitionId(node));
        patch.summary("patches " + getName(node));
        String description = node.getDescription() == null ? "patches " + getName(node) :
                node.getDescription();
        patch.description(description);
        BodyParameter bodyParameter = new BodyParameter()
                .name(getName(node) + ".body-param")
                .schema(definition)
                .description(getName(node) + " to be added or updated");
        bodyParameter.setRequired(true);
        patch.parameter(bodyParameter);

//        patch.response(200, new Response()
//                .schema(new RefProperty(getDefinitionId(node)))
//                .description(getName(node))); // TODO is there a 200 return?
        patch.response(204, new Response().description("Operation successful"));
        patch.response(401, new Response().description("Unauthorized"));
        patch.response(403, new Response().description("Forbidden"));
        return patch;
    }
}
