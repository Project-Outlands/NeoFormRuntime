package net.neoforged.neoform.runtime.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static net.neoforged.neoform.runtime.cli.ResultIds.CLIENT_RESOURCES;
import static net.neoforged.neoform.runtime.cli.ResultIds.CSV_MAPPING;
import static net.neoforged.neoform.runtime.cli.ResultIds.GAME_JAR;
import static net.neoforged.neoform.runtime.cli.ResultIds.GAME_JAR_NO_RECOMP;
import static net.neoforged.neoform.runtime.cli.ResultIds.GAME_JAR_WITH_SOURCES;
import static net.neoforged.neoform.runtime.cli.ResultIds.GAME_SOURCES;
import static net.neoforged.neoform.runtime.integration.ClassFileAssertions.assertTargetsJava;
import static org.assertj.core.api.Assertions.assertThat;

class NeoFormIntegrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"1.14.4", "1.16.5"})
    void usesMojmapWithEmbeddedMcpConfigMappings(String version, @TempDir Path tempDir) throws Exception {
        var fixture = NeoFormFixture.builder()
                .minecraftVersion(version)
                .javaVersion(8)
                .legacyMappings(LegacyMappings.withJoinedTsrg("Example -> a:\n    int count -> b\n    int read() -> c\n",
                        "a Example\n\tb field_123_a\n\tc ()I func_124_b\n"))
                .source("Example.java", "public class Example { public int field_123_a; public int func_124_b() { return field_123_a; } }")
                .compileLauncherSources()
                .build();
        var command = NfrtCommand.builder(tempDir, fixture)
                .result(GAME_SOURCES).result(GAME_JAR).result(GAME_JAR_NO_RECOMP).result(CSV_MAPPING).build();

        command.executeSuccessfully();

        assertThat(command.readResult(GAME_SOURCES, "Example.java")).contains("int count", "int read()", "return count;");
        assertThat(command.readResult(CSV_MAPPING, "fields.csv")).contains("field_123_a,count,0,");
        assertThat(command.readResult(CSV_MAPPING, "methods.csv")).contains("func_124_b,read,0,");
        for (var result : List.of(GAME_JAR, GAME_JAR_NO_RECOMP)) {
            try (var loader = new URLClassLoader(new URL[]{command.resultPath(result).toUri().toURL()})) {
                var example = loader.loadClass("Example");
                assertThat(example.getField("count").getType()).isEqualTo(int.class);
                assertThat(example.getMethod("read").getReturnType()).isEqualTo(int.class);
            }
        }
    }

    @Test
    void writesStandardNeoFormArtifacts(@TempDir Path tempDir) throws Exception {
        var fixture = NeoFormFixture.builder()
                .source("example/Example.java", "package example; public class Example {}")
                .source("assets/example.txt", "resource")
                .compileLauncherSources()
                .build();
        var command = NfrtCommand.builder(tempDir, fixture)
                .result(GAME_JAR)
                .result(GAME_SOURCES)
                .result(GAME_JAR_WITH_SOURCES)
                .result(GAME_JAR_NO_RECOMP)
                .result(CLIENT_RESOURCES)
                .build();

        command.executeSuccessfully();

        assertThat(command.resultEntries(GAME_JAR))
                .contains("example/Example.class", "assets/example.txt")
                .doesNotContain("example/Example.java");
        assertThat(command.resultEntries(GAME_SOURCES))
                .contains("example/Example.java", "assets/example.txt")
                .doesNotContain("example/Example.class");
        assertThat(command.resultEntries(GAME_JAR_WITH_SOURCES))
                .contains("example/Example.class", "example/Example.java", "assets/example.txt");
        assertThat(command.resultEntries(GAME_JAR_NO_RECOMP))
                .contains("example/Example.class")
                .doesNotContain("example/Example.java", "assets/example.txt");
        assertThat(command.resultEntries(CLIENT_RESOURCES))
                .contains("assets/example.txt")
                .doesNotContain("example/Example.class", "example/Example.java");
    }

    @ParameterizedTest(name = "use Eclipse compiler: {0}")
    @ValueSource(booleans = {false, true})
    void recompilesJavaSourcesAtConfiguredVersionAndPreservesResources(boolean useEclipseCompiler,
                                                                       @TempDir Path tempDir) throws Exception {
        var fixture = NeoFormFixture.builder()
                .source("example/Example.java", "package example; public class Example {}")
                .source("data.txt", "resource")
                .javaVersion(17)
                .compileLauncherSources()
                .build();
        var builder = NfrtCommand.builder(tempDir, fixture)
                .result(GAME_JAR)
                .result(GAME_JAR_NO_RECOMP);
        if (useEclipseCompiler) {
            builder.argument("--use-eclipse-compiler");
        }
        var command = builder.build();

        command.executeSuccessfully();

        assertThat(command.resultEntries(GAME_JAR))
                .containsExactlyInAnyOrder("example/Example.class", "data.txt");
        assertTargetsJava(command.readResultBytes(GAME_JAR, "example/Example.class"), 17);
        assertTargetsJava(command.readResultBytes(GAME_JAR_NO_RECOMP, "example/Example.class"), 17);
        assertThat(command.readResult(GAME_JAR, "data.txt")).isEqualTo("resource");
    }

    @Test
    void usesMinecraftAndNeoFormLibrariesForSourceTransformsAndRecompilation(@TempDir Path tempDir) throws Exception {
        var minecraftLibrary = NfrtFixtureSupport.compileSource(
                tempDir.resolve("minecraft-library"),
                "fixture/minecraft/MinecraftDependency.java",
                """
                        package fixture.minecraft;

                        public class MinecraftDependency {
                        }
                        """,
                21
        );
        var neoFormLibrary = NfrtFixtureSupport.compileSource(
                tempDir.resolve("neoform-library"),
                "fixture/neoform/NeoFormDependency.java",
                """
                        package fixture.neoform;

                        public class NeoFormDependency {
                        }
                        """,
                21
        );
        var fixture = NeoFormFixture.builder()
                .source("example/UsesConfiguredLibraries.java", """
                        package example;

                        import fixture.minecraft.MinecraftDependency;
                        import fixture.neoform.NeoFormDependency;

                        public class UsesConfiguredLibraries {
                            private MinecraftDependency minecraftDependency;
                            private NeoFormDependency neoFormDependency;
                        }
                        """)
                .minecraftLibrary("fixture:minecraft-dependency:1", minecraftLibrary)
                .neoFormLibrary("fixture:neoform-dependency:1")
                .build();
        var command = NfrtCommand.builder(tempDir, fixture)
                .artifact("fixture:neoform-dependency:1", neoFormLibrary)
                .validatedAccessTransformer("public example.UsesConfiguredLibraries minecraftDependency\n")
                .result(GAME_SOURCES)
                .result(GAME_JAR)
                .build();

        command.executeSuccessfully();

        assertThat(command.readResult(GAME_SOURCES, "example/UsesConfiguredLibraries.java"))
                .isEqualTo("""
                        package example;

                        import fixture.minecraft.MinecraftDependency;
                        import fixture.neoform.NeoFormDependency;

                        public class UsesConfiguredLibraries {
                            public MinecraftDependency minecraftDependency;
                            private NeoFormDependency neoFormDependency;
                        }
                        """);
        assertThat(command.resultEntries(GAME_JAR)).contains("example/UsesConfiguredLibraries.class");
    }

    @Test
    void compileClasspathOverrideReplacesConfiguredLibrariesEverywhere(@TempDir Path tempDir) throws Exception {
        var minecraftLibrary = NfrtFixtureSupport.compileSource(
                tempDir.resolve("minecraft-library"),
                "fixture/defaults/MinecraftDependency.java",
                """
                        package fixture.defaults;

                        public class MinecraftDependency {
                        }
                        """,
                21
        );
        var overrideLibrary = NfrtFixtureSupport.compileSource(
                tempDir.resolve("override-library"),
                "fixture/override/OverrideDependency.java",
                """
                        package fixture.override;

                        public class OverrideDependency {
                        }
                        """,
                21
        );
        var fixture = NeoFormFixture.builder()
                .source("example/UsesOverride.java", """
                        package example;

                        import fixture.override.OverrideDependency;

                        public class UsesOverride {
                            private OverrideDependency dependency;
                        }
                        """)
                .minecraftLibrary("fixture:minecraft-dependency:1", minecraftLibrary)
                .neoFormLibrary("fixture:missing-neoform-dependency:1")
                .build();
        var command = NfrtCommand.builder(tempDir, fixture)
                .argument("--compile-classpath=" + overrideLibrary.toAbsolutePath())
                .validatedAccessTransformer("public example.UsesOverride dependency\n")
                .result(GAME_JAR)
                .build();

        // The launcher manifest has already captured this artifact. Removing it proves that the override
        // replaces the Minecraft and NeoForm defaults instead of merely being appended to them.
        Files.delete(minecraftLibrary);
        command.executeSuccessfully();

        assertThat(command.resultEntries(GAME_JAR)).contains("example/UsesOverride.class");
    }
}
