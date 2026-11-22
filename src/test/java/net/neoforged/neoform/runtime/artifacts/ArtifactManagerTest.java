package net.neoforged.neoform.runtime.artifacts;

import net.neoforged.neoform.runtime.cache.CacheManager;
import net.neoforged.neoform.runtime.cache.LauncherInstallations;
import net.neoforged.neoform.runtime.cli.LockManager;
import net.neoforged.neoform.runtime.downloads.DownloadManager;
import net.neoforged.neoform.runtime.utils.MavenCoordinate;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactManagerTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void dynamicVersionUsesMatchingManifestArtifactWithoutRepositoryMetadata(boolean explicitRepository,
                                                                            @TempDir Path dir) throws Exception {
        var repository = dir.resolve("empty-repository").toUri();
        var selected = Files.createFile(dir.resolve("selected.jar"));
        var other = Files.createFile(dir.resolve("other.jar"));
        var manifest = new Properties();
        manifest.setProperty("net.minecraftforge:legacydev:0.2.3.10:fatjar", selected.toString());
        for (var coordinate : List.of("net.minecraftforge:legacydev:0.2.3.2:fatjar", "net.minecraftforge:legacydev:0.2.4.1:fatjar",
                "net.minecraftforge:legacydev:0.2.3.11", "net.minecraftforge:legacydev:0.2.3.12:fatjar@zip",
                "net.minecraftforge:legacydev:*:fatjar")) {
            manifest.setProperty(coordinate, other.toString());
        }
        var manifestPath = dir.resolve("artifacts.properties");
        try (var out = Files.newOutputStream(manifestPath)) {
            manifest.store(out, null);
        }

        try (var downloads = new DownloadManager(); var cache = new CacheManager(dir, null, dir.resolve("work"))) {
            var manager = new ArtifactManager(List.of(repository), cache, downloads, new LockManager(dir), repository,
                    new LauncherInstallations(List.of()));
            manager.loadArtifactManifest(manifestPath);
            var request = MavenCoordinate.parse("net.minecraftforge:legacydev:0.2.3.+:fatjar");
            var artifact = explicitRepository ? manager.get(request, repository) : manager.get(request);
            assertThat(artifact.path()).isEqualTo(selected);
        }
    }
}
