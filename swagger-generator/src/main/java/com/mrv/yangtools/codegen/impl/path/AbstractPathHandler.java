package com.mrv.yangtools.codegen.impl.path;

import com.mrv.yangtools.codegen.*;
import com.mrv.yangtools.codegen.impl.RpcContainerSchemaNode;

import io.swagger.models.*;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author bartosz.michalik@amartus.com
 */
public abstract class AbstractPathHandler implements PathHandler {
    protected final OpenAPI openAPI;
    protected final SchemaContext ctx;
    protected final org.opendaylight.yangtools.yang.model.api.Module module;
    protected boolean useModuleName;
    protected String data;
    protected String operations;
    protected final DataObjectBuilder dataObjectBuilder;
    protected final Set<TagGenerator> tagGenerators;
    protected final boolean fullCrud;

    protected AbstractPathHandler(SchemaContext ctx, org.opendaylight.yangtools.yang.model.api.Module modules, OpenAPI target, DataObjectBuilder objBuilder, Set<TagGenerator> generators, boolean fullCrud) {
        this.openAPI = target;
        this.ctx = ctx;
        this.module = modules;
        data = "/data/";
        operations = "/operations/";
        this.dataObjectBuilder = objBuilder;
        this.tagGenerators = generators;
        this.fullCrud = fullCrud;

        this.useModuleName = false;
    }

    public PathHandler useModuleName(boolean use) {
        this.useModuleName = use;
        return this;
    }

    @Override
    public void path(RpcDefinition rcp, PathSegment pathCtx) {
        ContainerSchemaNode input = rcp.getInput();
        ContainerSchemaNode output = rcp.getOutput();
        ContainerSchemaNode root = new RpcContainerSchemaNode(rcp);

        input = input.getChildNodes().isEmpty() ? null : input;
        output = output.getChildNodes().isEmpty() ? null : output;

        PathPrinter printer = getPrinter(pathCtx);

        Operation post = defaultOperation(pathCtx);

        post.addTagsItem(module.getName());
        if (input != null) {
            dataObjectBuilder.addModel(input);

            Schema<Object> inputModel = new Schema<>();
            inputModel.set$ref(dataObjectBuilder.getDefinitionId(input));

            post.summary("operates on " + dataObjectBuilder.getName(root));
            post.description("operates on " + dataObjectBuilder.getName(root));
            post.requestBody(new RequestBody()
                    .content(new Content().addMediaType("application/yang-data+json", new MediaType()
                            .schema(inputModel)))
                    .description(input.getDescription())
            );
        }

        if (output != null) {
            String description = output.getDescription();
            if (description == null) {
                description = "Correct response";
            }

            Schema<Object> refProperty = new Schema<>();
            refProperty.set$ref(dataObjectBuilder.getDefinitionId(root));

            dataObjectBuilder.addModel(root);
            if (post.getResponses() == null) post.setResponses(new ApiResponses());
            post.getResponses().addApiResponse("200", new ApiResponse()
                    .content(new Content().addMediaType("application/yang-data+json", new MediaType().schema(refProperty)))
                    .description(description));
        }
        if (post.getResponses() == null) post.setResponses(new ApiResponses());
        post.getResponses().addApiResponse("201", new ApiResponse().description("No response")); //no output body
        openAPI.path(operations + printer.path(), new PathItem().post(post));
    }

    protected abstract PathPrinter getPrinter(PathSegment pathCtx);


    protected abstract boolean generateModifyOperations(PathSegment pathCtx);

    protected PathItem operations(DataSchemaNode node, PathSegment pathCtx) {
        final PathItem path = new PathItem();
        List<String> tags = tags(pathCtx);

        path.get(new GetOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags));
        if (generateModifyOperations(pathCtx)) {
            path.put(new PutOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags));
            if (!pathCtx.forList()) {
                path.post(new PostOperationGenerator(pathCtx, dataObjectBuilder, false).execute(node).tags(tags));
            }
            path.delete(new DeleteOperationGenerator(pathCtx, dataObjectBuilder).execute(node).tags(tags));
        }

        return path;
    }

    private Operation defaultOperation(PathSegment pathCtx) {
        final Operation operation = new Operation();
        operation.response(400, new Response().description("Internal error"));
        operation.setParameters(pathCtx.params());
        return operation;
    }

    protected List<String> tags(PathSegment pathCtx) {
        List<String> tags = new ArrayList<>(tagGenerators.stream().flatMap(g -> g.tags(pathCtx).stream())
                .collect(Collectors.toSet()));
        Collections.sort(tags);
        String moduleName = pathCtx.stream().filter(p -> p.getModuleName() != null).map(PathSegment::getModuleName).findFirst().orElse(module.getName());
        tags.add(moduleName);
        return tags;
    }
}
