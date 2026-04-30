package io.github.xreatlabz.revprac.ports.arenas;

import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import java.util.Collection;
import java.util.Optional;

public interface ArenaRegistryRepository {

    Optional<ArenaDefinition> find(ArenaId arenaId);

    boolean create(ArenaDefinition arenaDefinition);

    Collection<ArenaDefinition> findAll();
}
