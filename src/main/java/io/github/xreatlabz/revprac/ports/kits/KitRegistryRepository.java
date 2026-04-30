package io.github.xreatlabz.revprac.ports.kits;

import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import java.util.Collection;
import java.util.Optional;

public interface KitRegistryRepository {

    Optional<KitDefinition> find(KitId kitId);

    void save(KitDefinition kitDefinition);

    Collection<KitDefinition> findAll();
}
