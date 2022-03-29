package com.mrv.yangtools.codegen.main;

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.mrv.yangtools.codegen.impl.path.rfc8040.PathHandlerBuilder;
import com.mrv.yangtools.codegen.impl.postprocessor.*;
import com.mrv.yangtools.common.ContextHelper;
import io.swagger.models.auth.BasicAuthDefinition;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.beust.jcommander.Parameter;

import com.mrv.yangtools.codegen.SwaggerGenerator;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    @Parameter(names = "-yang-dir", description = "Directory to search for YANG modules - defaults to current directory. " +
            "Multiple dirs might be separated by system path separator")
    public File yangDir = null;

    @Parameter(names = "-output", description = "File to generate, containing the output - defaults to stdout")
    public File output = null;

    @Parameter(description = "List of YANG module names to generate in swagger output")
    List<String> modules = null;

    @Parameter(names = "-format", description = "Output format of generated file - defaults to yaml with options of json or yaml")
    public SwaggerGenerator.Format outputFormat = SwaggerGenerator.Format.YAML;

    @Parameter(names = "-api-version", description = "Version of api generated - default 1.0")
    public String apiVersion = "1.0";

    @Parameter(names = "-simplify-hierarchy", description = "Use it to generate Swagger which with simplified inheritence model which can be used with standard code generators. Default false")
    public boolean simplified = false;

    //    @Option(name = "-use-namespaces", usage="Use namespaces in resource URI")
    public boolean useNamespaces = true; // Should always be true according to RFC 7951!

    @Parameter(names = "-fullCrud", description = "If the flag is set to false path are generated for GET operations only. Default true")
    public boolean fullCrud = true;

    @Parameter(names = "-elements", description = "Define YANG elements to focus on. Defaul DATA + RPC")
    public ElementType elementType = ElementType.DATA_AND_RPC;

    @Parameter(names = "-authentication", description = "Authentication definition")
    public AuthenticationMechanism authenticationMechanism = AuthenticationMechanism.NONE;

    @Parameter(names = "-strategy", description = "Use unpacking strategy")
    public SwaggerGenerator.Strategy strategy = SwaggerGenerator.Strategy.unpacking;

    public String contentType = "application/yang-data+json";

    @Parameter(names = "-host")
    public String host = "localhost:1234";

    @Parameter(names = "-basepath")
    public String basePath = "/restconf";

    public enum ElementType {
        DATA, RPC, DATA_AND_RPC;
    }

    public enum AuthenticationMechanism {
        BASIC, NONE
    }

    OutputStream out = System.out;

    public static void main(String... args) {

        Main main = new Main();

        try {
            JCommander.newBuilder()
                    .addObject(main)
                    .build()
                    .parse(args);
            main.init();
            main.generate();
        } catch (ParameterException e) {
            System.err.println(e.getMessage());
            e.usage();
        } catch (Throwable t) {
            log.error("Error while generating Swagger", t);
            System.exit(-1);
        }
    }

    protected void init() throws FileNotFoundException {
        if (output != null) {
            out = new FileOutputStream(output);
        }
    }

    protected void generate() throws IOException, ReactorException {
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.yang");

        final SchemaContext context = buildSchemaContext(yangDir, p -> matcher.matches(p.getFileName()));

        if (log.isInfoEnabled()) {
            String modulesSting = context.getModules().stream().map(ModuleIdentifier::getName).collect(Collectors.joining(", "));

            log.info("Modules found in the {} are {}", yangDir, modulesSting);
        }

        final Set<Module> toGenerate = context.getModules().stream().filter(m -> modules == null || modules.contains(m.getName()))
                .collect(Collectors.toSet());

        PathHandlerBuilder pathHandler = new PathHandlerBuilder();
        if (!fullCrud) {
            pathHandler.withoutFullCrud();
        }
        if (useNamespaces)
            pathHandler = pathHandler.useModuleName();

        validate(basePath);

        final SwaggerGenerator generator = new SwaggerGenerator(context, toGenerate, strategy)
        		.version(apiVersion)
                .format(outputFormat).consumes(contentType).produces(contentType)
                .host(host)
                .basePath(basePath)
                .pathHandler(pathHandler)
                .elements(map(elementType));

        generator
                .appendPostProcessor(new CollapseTypes());

        if (AuthenticationMechanism.BASIC.equals(authenticationMechanism)) {
            generator.appendPostProcessor(new AddSecurityDefinitions().withSecurityDefinition("api_sec", new BasicAuthDefinition()));
        }

        if (simplified) {
            generator.appendPostProcessor(new SingleParentInheritenceModel());
        }

        generator.appendPostProcessor(new Rfc4080PayloadWrapper());
//        generator.appendPostProcessor(new RemoveUnusedDefinitions());

        generator.generate(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    private void validate(String basePath) {
        URI.create(basePath);
    }

    private SchemaContext buildSchemaContext(File dir, Predicate<Path> accept)
            throws ReactorException {
//        if (dir.contains(File.pathSeparator)) {
//            return ContextHelper.getFromDir(Arrays.stream(dir.split(File.pathSeparator)).map(s -> FileSystems.getDefault().getPath(s)), accept);
//        } else {
            return ContextHelper.getFromDir(dir.toPath(), accept);
//        }
    }

    private SwaggerGenerator.Elements[] map(ElementType elementType) {
        switch (elementType) {
            case DATA:
                return new SwaggerGenerator.Elements[]{SwaggerGenerator.Elements.DATA};
            case RPC:
                return new SwaggerGenerator.Elements[]{SwaggerGenerator.Elements.RPC};
            case DATA_AND_RPC:
            default:
                return new SwaggerGenerator.Elements[]{SwaggerGenerator.Elements.DATA, SwaggerGenerator.Elements.RPC};
        }

    }
}
