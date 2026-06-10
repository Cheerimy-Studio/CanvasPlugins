package com.fabian.xclearlag.managers;

import com.fabian.xclearlag.XClearlag;
import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DependencyManager {

    private final XClearlag plugin;
    private final BukkitLibraryManager libraryManager;

    public DependencyManager(XClearlag plugin) {
        this.plugin = plugin;
        try {
            Path xapiPath = Paths.get(plugin.getDataFolder().getParent(), "X-API");
            Files.createDirectories(xapiPath);
            this.libraryManager = new BukkitLibraryManager(plugin, xapiPath);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not create X-API directory, using default: " + e.getMessage());
            this.libraryManager = new BukkitLibraryManager(plugin);
        }
        this.libraryManager.addMavenCentral();
        this.libraryManager.addSonatype();
        this.libraryManager.addRepository("https://repo.papermc.io/repository/maven-public/");
    }

    public void loadDependencies() {
        try {
            plugin.getLogger().info("Loading runtime dependencies via X-API...");
            loadAdventureDependencies();
            plugin.getLogger().info("All dependencies loaded successfully!");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load runtime dependencies! " + e.getMessage());
        }
    }

    private void loadAdventureDependencies() {
        Library adventureApi = Library.builder()
                .groupId("net.kyori")
                .artifactId("adventure-api")
                .version("4.14.0")
                .build();

        Library miniMessage = Library.builder()
                .groupId("net.kyori")
                .artifactId("adventure-text-minimessage")
                .version("4.14.0")
                .build();

        Library legacySerializer = Library.builder()
                .groupId("net.kyori")
                .artifactId("adventure-text-serializer-legacy")
                .version("4.14.0")
                .build();

        Library plainSerializer = Library.builder()
                .groupId("net.kyori")
                .artifactId("adventure-text-serializer-plain")
                .version("4.14.0")
                .build();

        Library key = Library.builder()
                .groupId("net.kyori")
                .artifactId("adventure-key")
                .version("4.14.0")
                .build();

        Library examinationApi = Library.builder()
                .groupId("net.kyori")
                .artifactId("examination-api")
                .version("1.3.0")
                .build();

        Library examinationString = Library.builder()
                .groupId("net.kyori")
                .artifactId("examination-string")
                .version("1.3.0")
                .build();

        libraryManager.loadLibrary(adventureApi);
        libraryManager.loadLibrary(miniMessage);
        libraryManager.loadLibrary(legacySerializer);
        libraryManager.loadLibrary(plainSerializer);
        libraryManager.loadLibrary(key);
        libraryManager.loadLibrary(examinationApi);
        libraryManager.loadLibrary(examinationString);
    }
}