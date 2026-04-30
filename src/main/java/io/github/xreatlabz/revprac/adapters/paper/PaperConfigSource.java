package io.github.xreatlabz.revprac.adapters.paper;

import io.github.xreatlabz.revprac.ports.config.ConfigSource;
import java.nio.file.Path;
import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;

public final class PaperConfigSource implements ConfigSource {

    private final FileConfiguration config;
    private final Path path;

    public PaperConfigSource(FileConfiguration config, Path path) {
        this.config = Objects.requireNonNull(config, "config");
        this.path = Objects.requireNonNull(path, "path");
    }

    @Override
    public Object rawValue(String queryPath) {
        return config.get(queryPath);
    }

    @Override
    public boolean hasPath(String queryPath) {
        return config.contains(queryPath);
    }

    @Override
    public String sourceDescription() {
        return path.toString();
    }

    public boolean booleanValueOrDefault(String queryPath, boolean defaultValue) {
        Object rawValue = rawValue(queryPath);
        return rawValue instanceof Boolean booleanValue ? booleanValue : defaultValue;
    }
}
