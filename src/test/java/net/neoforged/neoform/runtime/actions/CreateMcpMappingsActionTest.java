package net.neoforged.neoform.runtime.actions;

import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.srgutils.IMappingBuilder;
import net.neoforged.srgutils.IMappingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreateMcpMappingsActionTest {
    @TempDir
    Path tempDir;

    @Test
    void writesMcpMappingsAsSrgAndHeaderlessTsrgV1() throws IOException {
        var obfToSrgPath = tempDir.resolve("notch-to-srg.srg");
        var obfToSrgBuilder = IMappingBuilder.create("notch", "srg");
        var classMapping = obfToSrgBuilder.addClass("a", "net/minecraft/Test");
        classMapping.field("b", "field_1_value");
        classMapping.method("(I)V", "c", "func_2_run");
        classMapping.method("(Z)V", "d", "func_3_load");
        classMapping.method("(I)V", "e", "func_4_apply");
        classMapping.method("(I)V", "f", "func_5_broken");
        obfToSrgBuilder.build().getMap("notch", "srg")
                .write(obfToSrgPath, IMappingFile.Format.SRG, false);

        var mcpMappingsPath = tempDir.resolve("mcp.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(mcpMappingsPath))) {
            writeZipEntry(output, "fields.csv", "searge,name,side,desc\nfield_1_value,value,0,\n");
            writeZipEntry(output, "methods.csv", "searge,name,side,desc\nfunc_2_run,runThing,0,\nfunc_3_load,loadThing,0,\nfunc_4_apply,applyThing,0,\nfunc_5_broken,brokenThing,0,\n");
            // The parameter of the instance method func_3_load is at LVT slot 1 since slot 0 holds
            // 'this'; p_i*-keys are interim names for methods without a stable MCP name.
            // The slot of p_5_7_ matches neither the static nor the instance layout of (I)V, so no
            // parameter mapping can be derived from it.
            writeZipEntry(output, "params.csv", "param,name,side\np_2_0_,count,0\np_3_1_,flag,0\np_i4_0_,amount,0\np_5_7_,bogus,0\n");
        }

        var srgOutput = tempDir.resolve("srg-to-mcp.srg");
        var tsrgOutput = tempDir.resolve("srg-to-mcp.tsrg");
        var tsrg2Output = tempDir.resolve("srg-to-mcp-v2.tsrg");
        var mcpToSrgOutput = tempDir.resolve("mcp-to-srg.srg");
        var mcpToSrgTsrgOutput = tempDir.resolve("mcp-to-srg.tsrg");
        var notchToSrgOutput = tempDir.resolve("notch-to-srg-output.srg");
        var csvOutput = tempDir.resolve("csv-output.zip");

        var environment = mock(ProcessingEnvironment.class);
        when(environment.extractData("mappings")).thenReturn(obfToSrgPath);
        when(environment.getOutputPath("srgToMcp")).thenReturn(srgOutput);
        when(environment.getOutputPath("srgToMcpTsrg")).thenReturn(tsrgOutput);
        when(environment.getOutputPath("srgToMcpTsrg2")).thenReturn(tsrg2Output);
        when(environment.getOutputPath("mcpToSrg")).thenReturn(mcpToSrgOutput);
        when(environment.getOutputPath("mcpToSrgTsrg")).thenReturn(mcpToSrgTsrgOutput);
        when(environment.getOutputPath("notchToSrg")).thenReturn(notchToSrgOutput);
        when(environment.getOutputPath("csvMappings")).thenReturn(csvOutput);

        new CreateMcpMappingsAction(mcpMappingsPath, "mappings").run(environment);

        assertThat(Files.readAllLines(srgOutput)).contains(
                "FD: net/minecraft/Test/field_1_value net/minecraft/Test/value",
                "MD: net/minecraft/Test/func_2_run (I)V net/minecraft/Test/runThing (I)V"
        );
        assertThat(Files.readAllLines(tsrgOutput)).containsExactly(
                "net/minecraft/Test net/minecraft/Test",
                "\tfield_1_value value",
                "\tfunc_2_run (I)V runThing",
                "\tfunc_3_load (Z)V loadThing",
                "\tfunc_4_apply (I)V applyThing",
                "\tfunc_5_broken (I)V brokenThing"
        );
        assertThat(Files.readAllLines(mcpToSrgOutput)).contains(
                "FD: net/minecraft/Test/value net/minecraft/Test/field_1_value",
                "MD: net/minecraft/Test/runThing (I)V net/minecraft/Test/func_2_run (I)V"
        );
        assertThat(Files.readAllLines(mcpToSrgTsrgOutput)).containsExactly(
                "net/minecraft/Test net/minecraft/Test",
                "\tvalue field_1_value",
                "\tapplyThing (I)V func_4_apply",
                "\tbrokenThing (I)V func_5_broken",
                "\tloadThing (Z)V func_3_load",
                "\trunThing (I)V func_2_run"
        );
        // TSRG2 parameter indices are 0-based parameter ordinals, not the LVT slots used by the
        // CSV keys: the instance method parameter at CSV slot 1 is ordinal 0. No parameter line is
        // emitted for func_5_broken since its CSV slot matches no valid slot layout.
        assertThat(Files.readAllLines(tsrg2Output)).contains(
                "tsrg2 left right",
                "net/minecraft/Test net/minecraft/Test",
                "\tfield_1_value value",
                "\tfunc_2_run (I)V runThing",
                "\t\t0 p_2_0_ count",
                "\tfunc_3_load (Z)V loadThing",
                "\t\t0 p_3_1_ flag",
                "\tfunc_4_apply (I)V applyThing",
                "\t\t0 p_i4_0_ amount",
                "\tfunc_5_broken (I)V brokenThing"
        ).doesNotContain(
                "\t\t0 p_5_7_ bogus",
                "\t\t7 p_5_7_ bogus"
        );
    }

    private static void writeZipEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
