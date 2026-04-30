package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.ports.arenas.ArenaRegistryRepository;
import java.util.Collection;
import java.util.List;
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
    public void save(ArenaDefinition arenaDefinition) {
        Objects.requireNonNull(arenaDefinition, "arenaDefinition");
        arenas.put(arenaDefinition.id(), arenaDefinition);
    }

    @Override
    public Collection<ArenaDefinition> findAll() {
        return List.copyOf(arenas.values());
    }
}
