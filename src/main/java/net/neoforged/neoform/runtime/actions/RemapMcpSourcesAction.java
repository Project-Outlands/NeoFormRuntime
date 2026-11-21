package net.neoforged.neoform.runtime.actions;

import net.neoforged.neoform.runtime.cache.CacheKeyBuilder;
import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.neoform.runtime.utils.ToolCoordinate;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class RemapMcpSourcesAction extends ExternalJavaToolAction {
    private final CreateLibrariesOptionsFile parserLibraries = new CreateLibrariesOptionsFile();
    private final ExtensibleClasspath sourcepath;

    public RemapMcpSourcesAction(RecompileSourcesAction recompile) {
        super("net.neoforged.jst.cli.Main", ToolCoordinate.JAVA_SOURCE_TRANSFORMER, ToolCoordinate.JST_MCP);
        // Use the same live paths as recompile, including subsequent userdev and stub additions.
        parserLibraries.setClasspath(recompile.getClasspath());
        sourcepath = recompile.getSourcepath();
        setArgs(List.of("--enable-mcp", "--mcp-mode=full", "--mcp-csv", "{csv}",
                "--mcp-java-release=" + recompile.getTargetJavaVersion(), "--in-format=ARCHIVE", "--out-format=ARCHIVE", "{sources}", "{output}"));
    }

    @Override
    protected List<String> getProgramArguments(ProcessingEnvironment environment) throws IOException {
        var args = new ArrayList<>(List.of("--libraries-list", environment.getPathArgument(parserLibraries.writeFile(environment))));
        if (!sourcepath.isEmpty()) {
            var stubs = environment.getArtifactManager().resolveClasspath(sourcepath.getEffectiveClasspath());
            args.add("--classpath");
            args.add(stubs.stream().map(environment::getPathArgument).collect(Collectors.joining(File.pathSeparator)));
        }
        args.addAll(getArgs());
        return args;
    }

    @Override
    public void computeCacheKey(CacheKeyBuilder ck) {
        super.computeCacheKey(ck);
        parserLibraries.computeCacheKey(ck);
        sourcepath.computeCacheKey("MCP parser sourcepath", ck);
    }
}
