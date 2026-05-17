package io.github.xreatlabz.revprac.application.tournaments;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.tournaments.Tournament;
import io.github.xreatlabz.revprac.domain.tournaments.TournamentId;
import io.github.xreatlabz.revprac.ports.tournaments.TournamentRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class TournamentService {

    private final TournamentRepository tournamentRepository;

    public TournamentService(TournamentRepository tournamentRepository) {
        this.tournamentRepository = Objects.requireNonNull(tournamentRepository, "tournamentRepository");
    }

    public Tournament createTournament(String name, int maxEntrants) {
        Tournament tournament = Tournament.create(new TournamentId(UUID.randomUUID()), name, maxEntrants);
        if (!tournamentRepository.create(tournament)) {
            throw new IllegalStateException("tournament id already exists");
        }
        return tournament;
    }

    public Tournament openTournament(TournamentId tournamentId, Instant openedAt) {
        Objects.requireNonNull(tournamentId, "tournamentId");
        Objects.requireNonNull(openedAt, "openedAt");
        Tournament updated = requireTournament(tournamentId).open(openedAt);
        tournamentRepository.save(updated);
        return updated;
    }

    public Tournament register(TournamentId tournamentId, PlayerId playerId) {
        Objects.requireNonNull(tournamentId, "tournamentId");
        Objects.requireNonNull(playerId, "playerId");
        Tournament updated = requireTournament(tournamentId).register(playerId);
        tournamentRepository.save(updated);
        return updated;
    }

    public Tournament startTournament(TournamentId tournamentId, Instant startedAt) {
        Objects.requireNonNull(tournamentId, "tournamentId");
        Objects.requireNonNull(startedAt, "startedAt");
        Tournament updated = requireTournament(tournamentId).start(startedAt);
        tournamentRepository.save(updated);
        return updated;
    }

    public Tournament completeTournament(TournamentId tournamentId, PlayerId winnerId, Instant completedAt) {
        Objects.requireNonNull(tournamentId, "tournamentId");
        Objects.requireNonNull(winnerId, "winnerId");
        Objects.requireNonNull(completedAt, "completedAt");
        Tournament updated = requireTournament(tournamentId).complete(winnerId, completedAt);
        tournamentRepository.save(updated);
        return updated;
    }

    public Tournament status(TournamentId tournamentId) {
        Objects.requireNonNull(tournamentId, "tournamentId");
        return requireTournament(tournamentId);
    }

    private Tournament requireTournament(TournamentId tournamentId) {
        return tournamentRepository.find(tournamentId)
                .orElseThrow(() -> new IllegalStateException("tournament not found"));
    }
}
