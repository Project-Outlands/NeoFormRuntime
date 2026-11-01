package net.neoforged.neoform.runtime.compatibility;

import net.neoforged.neoform.runtime.actions.ExtensibleClasspath;
import net.neoforged.neoform.runtime.utils.MavenCoordinate;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Applies the classpath compatibility rules required by Cleanroom-style userdev
 * (Cleanroom itself and forks of it, such as Outlands).
 */
public final class CleanroomClasspath {
    private static final String CLEANROOM_GROUP = "com.cleanroommc";
    private static final String CLEANROOM_ARTIFACT = "cleanroom";
    private static final String OUTLANDS_GROUP = "top.outlands";
    private static final String OUTLANDS_ARTIFACT = "outlands";
    private static final MavenCoordinate LWJGLX = MavenCoordinate.parse("com.cleanroommc:lwjglx:1.0.0");

    /**
     * LWJGL2-era Minecraft libraries that are obsolete under Cleanroom. A {@code null} artifact id
     * excludes the whole group.
     */
    private record ObsoleteLibrary(String groupId, @Nullable String artifactId) {
        boolean matches(MavenCoordinate library) {
            return groupId.equals(library.groupId()) && (artifactId == null || artifactId.equals(library.artifactId()));
        }
    }

    private static final List<ObsoleteLibrary> OBSOLETE_MINECRAFT_LIBRARIES = List.of(
            new ObsoleteLibrary("org.lwjgl.lwjgl", null),
            new ObsoleteLibrary("oshi-project", null),
            new ObsoleteLibrary("net.java.jutils", null),
            new ObsoleteLibrary("com.mojang", "patchy"),
            new ObsoleteLibrary("com.ibm.icu", "icu4j-core-mojang"),
            new ObsoleteLibrary("io.netty", "netty-all"),
            new ObsoleteLibrary("net.java.dev.jna", "platform")
    );

    private CleanroomClasspath() {
    }

    public static void configureListLibrariesIfNeeded(
            String universalArtifact,
            Collection<MavenCoordinate> userdevLibraries,
            ExtensibleClasspath classpath) {
        if (!isCleanroomUniversalArtifact(universalArtifact)) {
            return;
        }

        excludeObsoleteMinecraftLibraries(classpath);
        addCleanroomUserdevLibraries(userdevLibraries, classpath);
    }

    public static boolean configureRecompileIfNeeded(
            String universalArtifact,
            Collection<MavenCoordinate> userdevLibraries,
            ExtensibleClasspath classpath) {
        if (!isCleanroomUniversalArtifact(universalArtifact)) {
            return false;
        }

        excludeObsoleteMinecraftLibraries(classpath);
        if (isCleanroomArtifact(universalArtifact)) {
            // Outlands carries its own LWJGL2-compat shims as sources compiled against real LWJGL3,
            // so the lwjglx emulation layer must not shadow it there.
            classpath.addMavenLibraries(List.of(LWJGLX));
        }
        addCleanroomUserdevLibraries(userdevLibraries, classpath);
        return true;
    }

    private static boolean isCleanroomUniversalArtifact(String universalArtifact) {
        return isCleanroomArtifact(universalArtifact) || isOutlandsArtifact(universalArtifact);
    }

    private static boolean isCleanroomArtifact(String universalArtifact) {
        return matchesArtifact(universalArtifact, CLEANROOM_GROUP, CLEANROOM_ARTIFACT);
    }

    private static boolean isOutlandsArtifact(String universalArtifact) {
        return matchesArtifact(universalArtifact, OUTLANDS_GROUP, OUTLANDS_ARTIFACT);
    }

    private static boolean matchesArtifact(String universalArtifact, String groupId, String artifactId) {
        if (!universalArtifact.matches("[^:]+:[^:]+:[^:@]+(?::[^@]+)?(?:@[^:]+)?")) {
            return false;
        }
        var universal = MavenCoordinate.parse(universalArtifact);
        return groupId.equals(universal.groupId()) && artifactId.equals(universal.artifactId());
    }

    private static void excludeObsoleteMinecraftLibraries(ExtensibleClasspath classpath) {
        for (var excluded : OBSOLETE_MINECRAFT_LIBRARIES) {
            if (excluded.artifactId() == null) {
                classpath.excludeMinecraftLibraryGroup(excluded.groupId());
            } else {
                classpath.excludeMinecraftLibrary(excluded.groupId(), excluded.artifactId());
            }
        }
    }

    private static void addCleanroomUserdevLibraries(
            Collection<MavenCoordinate> userdevLibraries, ExtensibleClasspath classpath) {
        classpath.addMavenLibraries(userdevLibraries.stream()
                .filter(library -> !isObsoleteLibrary(library))
                .filter(library -> !isLwjglx(library))
                .toList());
    }

    private static boolean isObsoleteLibrary(MavenCoordinate library) {
        return OBSOLETE_MINECRAFT_LIBRARIES.stream().anyMatch(excluded -> excluded.matches(library));
    }

    private static boolean isLwjglx(MavenCoordinate library) {
        return CLEANROOM_GROUP.equals(library.groupId()) && "lwjglx".equals(library.artifactId());
    }
}
