package net.neoforged.neoform.runtime.compatibility;

import net.neoforged.neoform.runtime.utils.MavenCoordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyMcpToolOverridesTest {
    private static final MavenCoordinate SPECIAL_SOURCE_1_11_3 = MavenCoordinate.parse("net.md-5:SpecialSource:1.11.3:shaded");
    private static final MavenCoordinate SPECIAL_SOURCE_1_11_6 = SPECIAL_SOURCE_1_11_3.withVersion("1.11.6");
    private static final MavenCoordinate MC_INJECTOR_3_8_4 = MavenCoordinate.parse("de.oceanlabs.mcp:mcinjector:3.8.4:fatjar");
    private static final MavenCoordinate MC_INJECTOR_3_8_12 = MC_INJECTOR_3_8_4.withVersion("3.8.12");

    @Test
    void overridesBothToolsForJava25() {
        assertThat(ToolOverrides.getToolOverride(25, "rename", SPECIAL_SOURCE_1_11_3))
                .isEqualTo(SPECIAL_SOURCE_1_11_6);
        assertThat(ToolOverrides.getToolOverride(25, "mcinject", MC_INJECTOR_3_8_4)).isEqualTo(MC_INJECTOR_3_8_12);
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 21, 24, 26})
    void leavesBothToolsAloneForOtherTargets(int javaTarget) {
        assertThat(ToolOverrides.getToolOverride(javaTarget, "rename", SPECIAL_SOURCE_1_11_3)).isNull();
        assertThat(ToolOverrides.getToolOverride(javaTarget, "mcinject", MC_INJECTOR_3_8_4)).isNull();
    }

    @Test
    void doesNotOverrideOtherToolsOrVersions() {
        assertThat(ToolOverrides.getToolOverride(25, "decompile", SPECIAL_SOURCE_1_11_3))
                .isNull();
        assertThat(ToolOverrides.getToolOverride(25, "rename", SPECIAL_SOURCE_1_11_6))
                .isNull();
        assertThat(ToolOverrides.getToolOverride(25, "rename", MavenCoordinate.parse("net.md-5:SpecialSource:1.8.3:shaded")))
                .isNull();
        assertThat(ToolOverrides.getToolOverride(25, "rename", MC_INJECTOR_3_8_4)).isNull();
        assertThat(ToolOverrides.getToolOverride(25, "mcinject", MC_INJECTOR_3_8_12)).isNull();
        assertThat(ToolOverrides.getToolOverride(25, "mcinject", MC_INJECTOR_3_8_4.withClassifier(""))).isNull();
    }
}
