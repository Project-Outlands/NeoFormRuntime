package net.neoforged.neoform.runtime.cli;

import net.neoforged.neoform.runtime.actions.ApplySourceTransformAction;
import net.neoforged.neoform.runtime.actions.CreateLibrariesOptionsFile;
import net.neoforged.neoform.runtime.actions.ExternalJavaToolAction;
import net.neoforged.neoform.runtime.artifacts.ClasspathItem;
import net.neoforged.neoform.runtime.compatibility.UserdevClasspath;
import net.neoforged.neoform.runtime.engine.NeoFormEngine;
import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.neoform.runtime.graph.ExecutionGraph;
import net.neoforged.neoform.runtime.manifests.MinecraftDownload;
import net.neoforged.neoform.runtime.manifests.MinecraftLibrary;
import net.neoforged.neoform.runtime.manifests.MinecraftVersionManifest;
import net.neoforged.neoform.runtime.utils.MavenCoordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RunNeoFormCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void configuresUserdevListLibrariesForAllExternalToolActions() {
        var graph = new ExecutionGraph();

        var decompileListLibraries = new CreateLibrariesOptionsFile();
        var decompileAction = new ExternalJavaToolAction(MavenCoordinate.parse("example:decompiler:1.0"));
        decompileAction.setListLibraries(decompileListLibraries);
        var decompileNode = graph.nodeBuilder("decompile");
        decompileNode.action(decompileAction);
        decompileNode.build();

        var transformSourcesAction = new ApplySourceTransformAction();
        var transformSourcesNode = graph.nodeBuilder("transformSources");
        transformSourcesNode.action(transformSourcesAction);
        transformSourcesNode.build();

        var userdevClasspath = UserdevClasspath.forArtifact(
                "com.cleanroommc:cleanroom:0.5.17-alpha:universal",
                List.of(
                        MavenCoordinate.parse("io.netty:netty-common:4.2.15.Final"),
                        MavenCoordinate.parse("org.lwjgl.lwjgl:lwjgl:2.9.4"),
                        MavenCoordinate.parse("com.cleanroommc:lwjglx:1.0.0")));
        RunNeoFormCommand.configureUserdevListLibraries(graph, userdevClasspath);

        var retained = minecraftLibrary("example:retained:1.0");
        var manifest = versionManifest(List.of(
                minecraftLibrary("org.lwjgl.lwjgl:lwjgl:2.9.4"),
                retained));

        assertThat(decompileListLibraries.getClasspath()
                .mergeWithMinecraftLibraries(manifest)
                .getEffectiveClasspath())
                .containsExactly(
                        ClasspathItem.of(retained),
                        ClasspathItem.of(MavenCoordinate.parse("io.netty:netty-common:4.2.15.Final")));
        assertThat(transformSourcesAction.getListLibraries().getClasspath()
                .mergeWithMinecraftLibraries(manifest)
                .getEffectiveClasspath())
                .containsExactly(
                        ClasspathItem.of(retained),
                        ClasspathItem.of(MavenCoordinate.parse("io.netty:netty-common:4.2.15.Final")));
    }

    @ParameterizedTest
    @CsvSource({", 1.11.3, 3.8.4", "24, 1.11.3, 3.8.4", "25, 1.11.6, 3.8.12", "26, 1.11.3, 3.8.4"})
    void selectsToolOverridesFromTopLevelJavaTarget(Integer javaTarget, String specialSourceVersion, String mcInjectorVersion) throws IOException {
        var config = """
                {
                  "spec": 4, "version": "1.21", %s
                  "data": {"patches": "patches/"}, "libraries": {},
                  "steps": {"joined": [
                    {"type": "downloadJson"},
                    {"type": "rename", "input": "{downloadJsonOutput}"},
                    {"type": "mcinject", "input": "{renameOutput}"},
                    {"type": "decompile", "input": "{mcinjectOutput}"},
                    {"type": "patch", "input": "{decompileOutput}"}
                  ]},
                  "functions": {
                    "rename": {"classpath": ["net.md-5:SpecialSource:1.11.3:shaded", "test:helper:1.0"],
                               "main_class": "net.md_5.specialsource.SpecialSource", "args": ["{input}", "{output}"]},
                    "mcinject": {"version": "de.oceanlabs.mcp:mcinjector:3.8.4:fatjar", "java_version": 25, "args": ["{input}", "{output}"]},
                    "decompile": {"version": "test:decompiler:1.0", "args": ["{input}", "{output}"]}
                  }
                }
                """.formatted(javaTarget == null ? "" : "\"java_target\": " + javaTarget + ",");
        var archive = tempDir.resolve("mcp_config.zip");
        writeZip(archive, List.of(new TestEntry("config.json", config), new TestEntry("patches/", "")));

        try (var engine = new NeoFormEngine(null, null, null, null)) {
            engine.loadNeoFormData(archive, "joined");
            var rename = (ExternalJavaToolAction) engine.getGraph().getRequiredNode("rename").action();
            assertThat(rename.getClasspath()).extracting(MavenCoordinate::toString)
                    .containsExactly("net.md-5:SpecialSource:" + specialSourceVersion + ":shaded", "test:helper:1.0");
            var mcinject = (ExternalJavaToolAction) engine.getGraph().getRequiredNode("mcinject").action();
            assertThat(mcinject.getClasspath()).extracting(MavenCoordinate::toString)
                    .containsExactly("de.oceanlabs.mcp:mcinjector:" + mcInjectorVersion + ":fatjar");
        }
    }

    @Test
    void legacyUniversalMetadataInjectionInjectsResourcesWithoutClasses() throws Exception {
        var recompiledJar = tempDir.resolve("recompiled.jar");
        writeZip(recompiledJar, List.of(
                new TestEntry("example/Recompiled.class", "recompiled")
        ));

        var universalJar = tempDir.resolve("universal.jar");
        writeZip(universalJar, List.of(
                new TestEntry("META-INF/services/example.Service", "example.ServiceProvider\n"),
                new TestEntry("META-INF/EXAMPLE.SF", "signature"),
                new TestEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"),
                new TestEntry("example/Recompiled.class", "universal replacement"),
                new TestEntry("example/Universal.class", "universal"),
                new TestEntry("assets/example/icon.png", "image")
        ));

        var outputJar = tempDir.resolve("output.jar");
        var environment = mock(ProcessingEnvironment.class);
        when(environment.getRequiredInputPath("input")).thenReturn(recompiledJar);
        when(environment.getOutputPath("output")).thenReturn(outputJar);

        try (var universalZip = new ZipFile(universalJar.toFile())) {
            var action = RunNeoFormCommand.createLegacyUniversalMetadataInjection(
                    universalZip,
                    List.of("^(?!excluded\\.txt$).*")
            );
            action.run(environment);
        }

        try (var output = new ZipFile(outputJar.toFile())) {
            assertThat(readEntry(output, "META-INF/services/example.Service"))
                    .isEqualTo("example.ServiceProvider\n");
            assertThat(readEntry(output, "example/Recompiled.class")).isEqualTo("recompiled");
            assertThat(output.getEntry("example/Universal.class")).isNull();
            assertThat(output.getEntry("META-INF/EXAMPLE.SF")).isNull();
            assertThat(readEntry(output, "META-INF/MANIFEST.MF")).isEqualTo("Manifest-Version: 1.0\n");
            assertThat(readEntry(output, "assets/example/icon.png")).isEqualTo("image");
        }
    }

    private static void writeZip(Path path, List<TestEntry> entries) throws IOException {
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries) {
                output.putNextEntry(new ZipEntry(entry.name()));
                output.write(entry.content().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private static String readEntry(ZipFile zip, String name) throws IOException {
        var entry = zip.getEntry(name);
        assertThat(entry).isNotNull();
        try (var input = zip.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static MinecraftVersionManifest versionManifest(List<MinecraftLibrary> libraries) {
        return new MinecraftVersionManifest("test", Map.of(), libraries, null, null, null, null, null);
    }

    private static MinecraftLibrary minecraftLibrary(String coordinate) {
        return new MinecraftLibrary(
                coordinate,
                new MinecraftLibrary.Downloads(new MinecraftDownload("", 0, null, null), Map.of()),
                List.of(),
                null);
    }

    private record TestEntry(String name, String content) {
    }
}
