package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.ports.arenas.ArenaRegistryRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryArenaRegistryRepository implements ArenaRegistryRepository {

    private final ConcurrentMap<ArenaId, ArenaDefinition> arenas = new ConcurrentHashMap<>();

    @Override
    public Optional<ArenaDefinition> find(ArenaId arenaId) {
        Objects.requireNonNull(arenaId, "arenaId");
        return Optional.ofNullable(arenas.get(arenaId));
    }

    @Override
    public boolean create(ArenaDefinition arenaDefinition) {
        Objects.requireNonNull(arenaDefinition, "arenaDefinition");
        return arenas.putIfAbsent(arenaDefinition.id(), arenaDefinition) == null;
    }

    @Override
    public void replaceAll(List<ArenaDefinition> arenaDefinitions) {
        Objects.requireNonNull(arenaDefinitions, "arenaDefinitions");
        Map<ArenaId, ArenaDefinition> replacement = new HashMap<>();
        for (ArenaDefinition arenaDefinition : arenaDefinitions) {
            Objects.requireNonNull(arenaDefinition, "arenaDefinition");
            if (replacement.putIfAbsent(arenaDefinition.id(), arenaDefinition) != null) {
                throw new IllegalArgumentException("Arena already exists: " + arenaDefinition.id().value());
            }
        }
        arenas.clear();
        arenas.putAll(replacement);
    }

    @Override
    public Collection<ArenaDefinition> findAll() {
        return List.copyOf(arenas.values());
    }
}
