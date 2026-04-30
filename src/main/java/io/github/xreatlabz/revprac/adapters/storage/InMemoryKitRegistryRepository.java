package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.ports.kits.KitRegistryRepository;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryKitRegistryRepository implements KitRegistryRepository {

    private final ConcurrentMap<KitId, KitDefinition> kits = new ConcurrentHashMap<>();

    @Override
    public Optional<KitDefinition> find(KitId kitId) {
        Objects.requireNonNull(kitId, "kitId");
        return Optional.ofNullable(kits.get(kitId));
    }

    @Override
    public boolean create(KitDefinition kitDefinition) {
        Objects.requireNonNull(kitDefinition, "kitDefinition");
        return kits.putIfAbsent(kitDefinition.id(), kitDefinition) == null;
    }

    @Override
    public Collection<KitDefinition> findAll() {
        return List.copyOf(kits.values());
    }
}
