package net.neoforged.neoform.runtime.compatibility;

import net.neoforged.neoform.runtime.utils.MavenCoordinate;
import org.jetbrains.annotations.Nullable;

/**Substitutes incompatible tools in MCP configs.*/
public final class ToolOverrides {
    private static final MavenCoordinate SPECIAL_SOURCE_1_11_3 = MavenCoordinate.parse("net.md-5:SpecialSource:1.11.3:shaded");
    private static final MavenCoordinate SPECIAL_SOURCE_1_11_6 = SPECIAL_SOURCE_1_11_3.withVersion("1.11.6");
    private static final MavenCoordinate MC_INJECTOR_3_8_4 = MavenCoordinate.parse("de.oceanlabs.mcp:mcinjector:3.8.4:fatjar");
    private static final MavenCoordinate MC_INJECTOR_3_8_12 = MC_INJECTOR_3_8_4.withVersion("3.8.12");

    private ToolOverrides() {
    }

    /**
     * Returns the tool artifact override for the given function, or {@code null} if none applies.
     * Applies to both the legacy {@code version} field and individual {@code classpath} entries.
     */
    @Nullable
    public static MavenCoordinate getToolOverride(int javaTarget, String functionId, MavenCoordinate toolArtifact) {
        // Cleanroom 0.5.x ~ 0.6.x
        // ASM in some tools is outdated, making recompile fail
        if (javaTarget == 25) {
            if ("rename".equals(functionId) && toolArtifact.equals(SPECIAL_SOURCE_1_11_3)) {
                return SPECIAL_SOURCE_1_11_6;
            }
            if ("mcinject".equals(functionId) && toolArtifact.equals(MC_INJECTOR_3_8_4)) {
                return MC_INJECTOR_3_8_12;
            }
        }
        return null;
    }
}
