package io.github.xreatlabz.revprac.application.kits;

import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.ports.kits.KitRegistryRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

public final class KitRegistryService {

    private static final Comparator<KitDefinition> BY_ID =
            Comparator.comparing(kitDefinition -> kitDefinition.id().value());

    private final KitRegistryRepository kitRegistryRepository;
    private final ReentrantLock mutationLock = new ReentrantLock();

    public KitRegistryService(KitRegistryRepository kitRegistryRepository) {
        this.kitRegistryRepository = Objects.requireNonNull(kitRegistryRepository, "kitRegistryRepository");
    }

    public void register(KitDefinition kitDefinition) {
        if (kitDefinition == null) {
            throw new IllegalArgumentException("kitDefinition must not be null");
        }

        mutationLock.lock();
        try {
            if (!kitRegistryRepository.create(kitDefinition)) {
                throw new IllegalArgumentException("Kit already exists: " + kitDefinition.id().value());
            }
        } finally {
            mutationLock.unlock();
        }
    }

    public List<KitDefinition> kits() {
        return kitRegistryRepository.findAll().stream()
                .sorted(BY_ID)
                .toList();
    }

    public List<KitDefinition> enabledKits() {
        return kitRegistryRepository.findAll().stream()
                .filter(KitDefinition::enabled)
                .sorted(BY_ID)
                .toList();
    }
}
