package io.github.xreatlabz.revprac.application.arenas;

import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservation;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId;
import io.github.xreatlabz.revprac.ports.arenas.ArenaRegistryRepository;
import io.github.xreatlabz.revprac.ports.arenas.ArenaResetPort;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public final class ArenaRegistryService {

    private static final Comparator<ArenaDefinition> BY_ID =
            Comparator.comparing(arenaDefinition -> arenaDefinition.id().value());

    private final ArenaRegistryRepository arenaRegistryRepository;
    private final ArenaResetPort arenaResetPort;
    private final ReentrantLock mutationLock = new ReentrantLock();
    private final Map<ArenaReservationId, ArenaReservation> activeReservations = new HashMap<>();
    private final Map<ArenaId, ArenaReservationId> reservationsByArenaId = new HashMap<>();
    private final Set<ArenaId> resettingArenaIds = new HashSet<>();

    public ArenaRegistryService(ArenaRegistryRepository arenaRegistryRepository, ArenaResetPort arenaResetPort) {
        this.arenaRegistryRepository = Objects.requireNonNull(arenaRegistryRepository, "arenaRegistryRepository");
        this.arenaResetPort = Objects.requireNonNull(arenaResetPort, "arenaResetPort");
    }

    public void register(ArenaDefinition arenaDefinition) {
        if (arenaDefinition == null) {
            throw new IllegalArgumentException("arenaDefinition must not be null");
        }

        mutationLock.lock();
        try {
            ArenaId arenaId = arenaDefinition.id();
            if (!arenaRegistryRepository.create(arenaDefinition)) {
                throw new IllegalArgumentException("Arena already exists: " + arenaId.value());
            }
        } finally {
            mutationLock.unlock();
        }
    }

    public List<ArenaDefinition> arenas() {
        return arenaRegistryRepository.findAll().stream()
                .sorted(BY_ID)
                .toList();
    }

    public ArenaReservation reserve(ArenaId arenaId, String ownerKey) {
        Objects.requireNonNull(arenaId, "arenaId");

        mutationLock.lock();
        try {
            ArenaDefinition arenaDefinition = arenaRegistryRepository.find(arenaId)
                    .orElseThrow(() -> new IllegalStateException("Arena is not registered: " + arenaId.value()));
            if (!arenaDefinition.enabled()) {
                throw new IllegalStateException("Arena is disabled: " + arenaId.value());
            }
            if (reservationsByArenaId.containsKey(arenaId)) {
                throw new IllegalStateException("Arena is already reserved: " + arenaId.value());
            }
            if (resettingArenaIds.contains(arenaId)) {
                throw new IllegalStateException("Arena is resetting: " + arenaId.value());
            }

            ArenaReservation reservation =
                    new ArenaReservation(new ArenaReservationId(UUID.randomUUID()), arenaId, ownerKey);
            activeReservations.put(reservation.reservationId(), reservation);
            reservationsByArenaId.put(arenaId, reservation.reservationId());
            return reservation;
        } finally {
            mutationLock.unlock();
        }
    }

    public void release(ArenaReservationId reservationId) {
        Objects.requireNonNull(reservationId, "reservationId");

        ArenaDefinition arenaDefinitionToReset;
        ArenaId arenaIdToReset;
        mutationLock.lock();
        try {
            ArenaReservation reservation = activeReservations.remove(reservationId);
            if (reservation == null) {
                throw new IllegalStateException("Unknown reservation: " + reservationId.value());
            }

            arenaIdToReset = reservation.arenaId();
            arenaDefinitionToReset = arenaRegistryRepository.find(arenaIdToReset)
                    .orElseThrow(() -> new IllegalStateException(
                            "Arena is not registered: " + arenaIdToReset.value()));
            reservationsByArenaId.remove(arenaIdToReset);
            resettingArenaIds.add(arenaIdToReset);
        } finally {
            mutationLock.unlock();
        }

        try {
            arenaResetPort.reset(arenaDefinitionToReset);
        } finally {
            mutationLock.lock();
            try {
                resettingArenaIds.remove(arenaIdToReset);
            } finally {
                mutationLock.unlock();
            }
        }
    }
}
