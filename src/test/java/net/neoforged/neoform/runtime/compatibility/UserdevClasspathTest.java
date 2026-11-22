package net.neoforged.neoform.runtime.compatibility;

import net.neoforged.neoform.runtime.actions.ExtensibleClasspath;
import net.neoforged.neoform.runtime.artifacts.ClasspathItem;
import net.neoforged.neoform.runtime.manifests.MinecraftDownload;
import net.neoforged.neoform.runtime.manifests.MinecraftLibrary;
import net.neoforged.neoform.runtime.manifests.MinecraftVersionManifest;
import net.neoforged.neoform.runtime.utils.MavenCoordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class UserdevClasspathTest {
    private static final String CLEANROOM_UNIVERSAL = "com.cleanroommc:cleanroom:0.5.17-alpha:universal";
    private static final String OUTLANDS_UNIVERSAL = "top.outlands:outlands:1.0.0:universal";

    @ParameterizedTest
    @ValueSource(strings = {CLEANROOM_UNIVERSAL, OUTLANDS_UNIVERSAL})
    void usesFilteredUserdevLibrariesForListLibraries(String universalArtifact) {
        var retained = minecraftLibrary("example:retained:1.0");
        var manifest = versionManifest(List.of(
                minecraftLibrary("org.lwjgl.lwjgl:lwjgl:2.9.4"),
                minecraftLibrary("oshi-project:oshi-core:1.1"),
                minecraftLibrary("net.java.jutils:jutils:1.0.0"),
                minecraftLibrary("com.mojang:patchy:1.1"),
                minecraftLibrary("com.ibm.icu:icu4j-core-mojang:51.2"),
                minecraftLibrary("io.netty:netty-all:4.1.9.Final"),
                minecraftLibrary("net.java.dev.jna:platform:3.4.0"),
                retained));
        var classpath = new ExtensibleClasspath();

        UserdevClasspath.forArtifact(universalArtifact, userdevLibraries()).configureListLibraries(classpath);

        assertThat(classpath.mergeWithMinecraftLibraries(manifest).getEffectiveClasspath())
                .containsExactly(
                        ClasspathItem.of(retained),
                        ClasspathItem.of(MavenCoordinate.parse("io.netty:netty-common:4.2.15.Final")),
                        ClasspathItem.of(MavenCoordinate.parse("com.ibm.icu:icu4j:78.3")),
                        ClasspathItem.of(MavenCoordinate.parse("com.github.oshi:oshi-core-ffm:7.3.2")),
                        ClasspathItem.of(MavenCoordinate.parse("net.java.dev.jna:jna-platform:5.19.1")),
                        ClasspathItem.of(MavenCoordinate.parse("com.cleanroommc:lwjglxx:1.1.21")),
                        ClasspathItem.of(MavenCoordinate.parse("org.scala-lang:scala-xml_2.11:1.0.2")));
    }

    @Test
    void addsLwjglxOnlyOnceToCleanroomRecompileClasspath() {
        var retained = minecraftLibrary("example:retained:1.0");
        var manifest = versionManifest(List.of(
                minecraftLibrary("org.lwjgl.lwjgl:lwjgl:2.9.4"),
                retained));
        var classpath = new ExtensibleClasspath();

        var rules = UserdevClasspath.forArtifact(CLEANROOM_UNIVERSAL, userdevLibraries());
        rules.configureRecompile(classpath);
        rules.configureRecompile(classpath);

        assertThat(classpath.mergeWithMinecraftLibraries(manifest).getEffectiveClasspath())
                .containsExactly(
                        ClasspathItem.of(retained),
                        ClasspathItem.of(MavenCoordinate.parse("com.cleanroommc:lwjglx:1.0.0")),
                        ClasspathItem.of(MavenCoordinate.parse("io.netty:netty-common:4.2.15.Final")),
                        ClasspathItem.of(MavenCoordinate.parse("com.ibm.icu:icu4j:78.3")),
                        ClasspathItem.of(MavenCoordinate.parse("com.github.oshi:oshi-core-ffm:7.3.2")),
                        ClasspathItem.of(MavenCoordinate.parse("net.java.dev.jna:jna-platform:5.19.1")),
                        ClasspathItem.of(MavenCoordinate.parse("com.cleanroommc:lwjglxx:1.1.21")),
                        ClasspathItem.of(MavenCoordinate.parse("org.scala-lang:scala-xml_2.11:1.0.2")));
    }

    @Test
    void excludesUserdevLwjglxFromOutlandsRecompileClasspath() {
        var retained = minecraftLibrary("example:retained:1.0");
        var manifest = versionManifest(List.of(
                minecraftLibrary("org.lwjgl.lwjgl:lwjgl:2.9.4"),
                retained));
        var classpath = new ExtensibleClasspath();

        UserdevClasspath.forArtifact(OUTLANDS_UNIVERSAL, userdevLibraries()).configureRecompile(classpath);

        assertThat(classpath.mergeWithMinecraftLibraries(manifest).getEffectiveClasspath())
                .containsExactly(
                        ClasspathItem.of(retained),
                        ClasspathItem.of(MavenCoordinate.parse("io.netty:netty-common:4.2.15.Final")),
                        ClasspathItem.of(MavenCoordinate.parse("com.ibm.icu:icu4j:78.3")),
                        ClasspathItem.of(MavenCoordinate.parse("com.github.oshi:oshi-core-ffm:7.3.2")),
                        ClasspathItem.of(MavenCoordinate.parse("net.java.dev.jna:jna-platform:5.19.1")),
                        ClasspathItem.of(MavenCoordinate.parse("com.cleanroommc:lwjglxx:1.1.21")),
                        ClasspathItem.of(MavenCoordinate.parse("org.scala-lang:scala-xml_2.11:1.0.2")));
    }

    @Test
    void normalizesForge1122ScalaLibrariesInBothStagesWithoutRemovingLwjgl2() {
        var rules = UserdevClasspath.forArtifact("net.minecraftforge:forge:1.12.2-14.23.5.2860:universal", coordinates(
                "org.scala-lang:scala-library:2.11.1",
                "org.scala-lang.plugins:scala-continuations-library_2.11:1.0.2_mc",
                "org.scala-lang.plugins:scala-continuations-plugin_2.11.1:1.0.2_mc",
                "org.scala-lang:scala-actors-migration_2.11:1.1.0",
                "org.scala-lang:scala-parser-combinators_2.11:1.0.1",
                "org.scala-lang:scala-swing_2.11:1.0.1",
                "org.scala-lang:scala-xml_2.11:1.0.2:tests@zip",
                "org.ow2.asm:asm:5.2",
                "com.cleanroommc:lwjglx:0.9.0"));
        var listLibraries = new ExtensibleClasspath();
        var recompile = new ExtensibleClasspath();
        rules.configureListLibraries(listLibraries);
        rules.configureRecompile(recompile);
        rules.configureRecompile(recompile);

        var manifest = versionManifest(List.of(minecraftLibrary("org.lwjgl.lwjgl:lwjgl:2.9.4")));
        for (var classpath : List.of(listLibraries, recompile)) {
            assertThat(classpath.mergeWithMinecraftLibraries(manifest).getEffectiveClasspath())
                    .extracting(ClasspathItem::toString)
                    .containsExactly("org.lwjgl.lwjgl:lwjgl:2.9.4", "org.scala-lang:scala-library:2.11.1",
                            "org.scala-lang.modules:scala-parser-combinators_2.11:1.0.1",
                            "org.scala-lang.modules:scala-swing_2.11:1.0.1",
                            "org.scala-lang.modules:scala-xml_2.11:1.0.2:tests@zip",
                            "org.ow2.asm:asm:5.2", "com.cleanroommc:lwjglx:0.9.0");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "net.minecraftforge:forge:1.12.1-14.22.1.2485:universal",
            "net.neoforged:forge:1.20.1-47.1.54:universal",
            "com.cleanroommc:outlands:1.0:universal",
            "top.outlands:cleanroom:1.0:universal",
            "C:\\temp\\neoforge-universal.jar"
    })
    void defaultAddsUnmodifiedLibrariesOnlyForRecompile(String universalArtifact) {
        var lwjgl2 = minecraftLibrary("org.lwjgl.lwjgl:lwjgl:2.9.4");
        var manifest = versionManifest(List.of(lwjgl2));
        var libraries = coordinates("org.scala-lang:scala-xml_2.11:1.0.2", "com.cleanroommc:lwjglx:0.9.0");
        var existing = ClasspathItem.of(MavenCoordinate.parse("example:existing:1.0"));
        var listLibraries = new ExtensibleClasspath();
        listLibraries.add(existing);
        var recompile = listLibraries.copy();

        var rules = UserdevClasspath.forArtifact(universalArtifact, libraries);
        rules.configureListLibraries(listLibraries);
        rules.configureRecompile(recompile);

        assertThat(listLibraries.mergeWithMinecraftLibraries(manifest).getEffectiveClasspath())
                .containsExactly(ClasspathItem.of(lwjgl2), existing);
        assertThat(recompile.mergeWithMinecraftLibraries(manifest).getEffectiveClasspath())
                .containsExactly(ClasspathItem.of(lwjgl2), existing, ClasspathItem.of(libraries.get(0)), ClasspathItem.of(libraries.get(1)));
    }

    private static List<MavenCoordinate> userdevLibraries() {
        return coordinates(
                "io.netty:netty-common:4.2.15.Final",
                "com.ibm.icu:icu4j:78.3",
                "com.github.oshi:oshi-core-ffm:7.3.2",
                "net.java.dev.jna:jna-platform:5.19.1",
                "com.cleanroommc:lwjglxx:1.1.21",
                "org.scala-lang:scala-xml_2.11:1.0.2",
                "org.lwjgl.lwjgl:lwjgl:2.9.4",
                "oshi-project:oshi-core:1.1",
                "net.java.jutils:jutils:1.0.0",
                "com.mojang:patchy:1.1",
                "com.ibm.icu:icu4j-core-mojang:51.2",
                "io.netty:netty-all:4.1.9.Final",
                "net.java.dev.jna:platform:3.4.0",
                "com.cleanroommc:lwjglx:0.9.0");
    }

    private static List<MavenCoordinate> coordinates(String... coordinates) {
        return Stream.of(coordinates).map(MavenCoordinate::parse).toList();
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
}
