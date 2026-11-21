package net.neoforged.neoform.runtime.actions;

import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.neoform.runtime.utils.ToolCoordinate;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class CreateMcpMappingsAction extends ExternalJavaToolAction {
    private static final Map<String, String> OUTPUTS = Map.of(
            "srgToMcp", "srgToMcp.srg", "srgToMcpTsrg", "srgToMcp.tsrg", "srgToMcpTsrg2", "srgToMcp.tsrg2",
            "mcpToSrg", "mcpToSrg.srg", "mcpToSrgTsrg", "mcpToSrg.tsrg", "notchToSrg", "notchToSrg.srg",
            "csvMappings", "csvMappings.zip");

    public CreateMcpMappingsAction() {
        super(ToolCoordinate.JST_MCP);
        addDataSourceDependency("mappings");
        setArgs(List.of("--csv", "{csv}", "--obf-to-srg", "{mappings}", "--output", "export"));
    }

    @Override
    public void run(ProcessingEnvironment environment) throws IOException, InterruptedException {
        super.run(environment);
        var output = environment.getWorkspace().resolve("export");
        OUTPUTS.forEach((id, file) -> environment.setOutput(id, output.resolve(file)));
    }
}
