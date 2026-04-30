package io.github.xreatlabz.revprac.application.arenas;

import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservation;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId;
import io.github.xreatlabz.revprac.ports.arenas.ArenaRegistryRepository;
import io.github.xreatlabz.revprac.ports.arenas.ArenaResetPort;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            if (arenaRegistryRepository.find(arenaId).isPresent()) {
                throw new IllegalArgumentException("Arena already exists: " + arenaId.value());
            }
            arenaRegistryRepository.save(arenaDefinition);
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

        mutationLock.lock();
        try {
            ArenaReservation reservation = activeReservations.get(reservationId);
            if (reservation == null) {
                throw new IllegalStateException("Unknown reservation: " + reservationId.value());
            }

            ArenaDefinition arenaDefinition = arenaRegistryRepository.find(reservation.arenaId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Arena is not registered: " + reservation.arenaId().value()));

            arenaResetPort.reset(arenaDefinition);
            activeReservations.remove(reservationId);
            reservationsByArenaId.remove(reservation.arenaId());
        } finally {
            mutationLock.unlock();
        }
    }
}
