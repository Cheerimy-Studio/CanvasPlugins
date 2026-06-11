package com.fabian.xclearlag.managers;

import com.fabian.xclearlag.XClearlag;
import com.fabian.xclearlag.utils.DebugLogger;
import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;

public class DependencyManager {

    private final XClearlag plugin;
    private final BukkitLibraryManager libraryManager;

    public DependencyManager(XClearlag plugin) {
        this.plugin = plugin;
        this.libraryManager = new BukkitLibraryManager(plugin);
        this.libraryManager.addMavenCentral();
        this.libraryManager.addSonatype();
        this.libraryManager.addRepository("https://repo.papermc.io/repository/maven-public/");
    }

    public void loadDependencies() {
        try {
            plugin.getLogger().info("Loading runtime dependencies via X-API...");
            DebugLogger.debug("Dependency", "Loading Adventure dependencies...");
            loadAdventureDependencies();
            plugin.getLogger().info("All dependencies loaded successfully!");
            DebugLogger.debug("Dependency", "All dependencies loaded.");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load runtime dependencies! " + e.getMessage());
            DebugLogger.debug("Dependency", "Failed to load dependencies!", e);
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