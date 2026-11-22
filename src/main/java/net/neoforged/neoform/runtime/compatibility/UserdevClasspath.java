package net.neoforged.neoform.runtime.compatibility;

import net.neoforged.neoform.runtime.actions.ExtensibleClasspath;
import net.neoforged.neoform.runtime.utils.MavenCoordinate;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**Classpath additions and Minecraft library exclusions for one userdev archive.*/
public final class UserdevClasspath {
    private static final MavenCoordinate LWJGLX = MavenCoordinate.parse("com.cleanroommc:lwjglx:1.0.0");

    private enum Profile {
        DEFAULT, FORGE_1_12_2, CLEANROOM, OUTLANDS;

        static Profile forArtifact(String universalArtifact) {
            if (!universalArtifact.matches("[^:@]+:[^:@]+:[^:@]+(?::[^:@]+)?(?:@[^:@]+)?")) {
                return DEFAULT;
            }
            var universal = MavenCoordinate.parse(universalArtifact);
            return switch (universal.groupId() + ":" + universal.artifactId()) {
                case "net.minecraftforge:forge" -> universal.version().startsWith("1.12.2-") ? FORGE_1_12_2 : DEFAULT;
                case "com.cleanroommc:cleanroom" -> CLEANROOM;
                case "top.outlands:outlands" -> OUTLANDS;
                default -> DEFAULT;
            };
        }
    }

    /**
     * A {@code null} artifact id excludes the whole group.
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

    private final List<ObsoleteLibrary> excludedMinecraftLibraries;
    private final List<MavenCoordinate> listLibraries;
    private final List<MavenCoordinate> recompileLibraries;

    private UserdevClasspath(List<ObsoleteLibrary> excludedMinecraftLibraries, List<MavenCoordinate> listLibraries,
                            List<MavenCoordinate> recompileLibraries) {
        this.excludedMinecraftLibraries = excludedMinecraftLibraries;
        this.listLibraries = listLibraries;
        this.recompileLibraries = recompileLibraries;
    }

    public static UserdevClasspath forArtifact(String universalArtifact, Collection<MavenCoordinate> userdevLibraries) {
        return switch (Profile.forArtifact(universalArtifact)) {
            case FORGE_1_12_2 -> {
                var normalized = userdevLibraries.stream().map(UserdevClasspath::normalizeForgeLibrary)
                        .filter(Objects::nonNull).toList();
                yield new UserdevClasspath(List.of(), normalized, normalized);
            }
            case CLEANROOM -> modernized(userdevLibraries, List.of(LWJGLX));
            // Outlands compiles its own LWJGL2 shims against LWJGL3; lwjglx would shadow them.
            case OUTLANDS -> modernized(userdevLibraries, List.of());
            case DEFAULT -> new UserdevClasspath(List.of(), List.of(), List.copyOf(userdevLibraries));
        };
    }

    public void configureListLibraries(ExtensibleClasspath classpath) {
        configure(classpath, listLibraries);
    }

    public void configureRecompile(ExtensibleClasspath classpath) {
        configure(classpath, recompileLibraries);
    }

    private void configure(ExtensibleClasspath classpath, List<MavenCoordinate> libraries) {
        for (var excluded : excludedMinecraftLibraries) {
            if (excluded.artifactId() == null) {
                classpath.excludeMinecraftLibraryGroup(excluded.groupId());
            } else {
                classpath.excludeMinecraftLibrary(excluded.groupId(), excluded.artifactId());
            }
        }
        classpath.addMavenLibraries(libraries);
    }

    private static UserdevClasspath modernized(Collection<MavenCoordinate> userdevLibraries, List<MavenCoordinate> recompileExtras) {
        var filtered = userdevLibraries.stream()
                .filter(library -> !isObsoleteLibrary(library))
                .filter(library -> !isLwjglx(library))
                .toList();
        var recompile = Stream.concat(recompileExtras.stream(), filtered.stream()).toList();
        return new UserdevClasspath(OBSOLETE_MINECRAFT_LIBRARIES, filtered, recompile);
    }

    private static boolean isObsoleteLibrary(MavenCoordinate library) {
        return OBSOLETE_MINECRAFT_LIBRARIES.stream().anyMatch(excluded -> excluded.matches(library));
    }

    private static boolean isLwjglx(MavenCoordinate library) {
        return LWJGLX.groupId().equals(library.groupId()) && LWJGLX.artifactId().equals(library.artifactId());
    }

    private static @Nullable MavenCoordinate normalizeForgeLibrary(MavenCoordinate library) {
        if (library.groupId().equals("org.scala-lang.plugins")
                || library.groupId().equals("org.scala-lang") && library.artifactId().equals("scala-actors-migration_2.11")) {
            return null;
        }

        var hasMoved = switch (library.artifactId()) {
            case "scala-parser-combinators_2.11", "scala-swing_2.11" -> library.version().equals("1.0.1");
            case "scala-xml_2.11" -> library.version().equals("1.0.2");
            default -> false;
        };
        if (!library.groupId().equals("org.scala-lang") || !hasMoved) {
            return library;
        }

        return new MavenCoordinate("org.scala-lang.modules", library.artifactId(), library.extension(),
                library.classifier(), library.version());
    }
}
