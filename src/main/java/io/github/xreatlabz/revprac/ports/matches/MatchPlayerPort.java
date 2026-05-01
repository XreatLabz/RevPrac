package io.github.xreatlabz.revprac.ports.matches;

import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchSide;
import io.github.xreatlabz.revprac.domain.players.PlayerId;

public interface MatchPlayerPort {

    boolean isOnline(PlayerId playerId);

    void prepareCombatant(
            PlayerId playerId,
            Match match,
            MatchSide side,
            ArenaDefinition arenaDefinition,
            KitDefinition kitDefinition);

    void prepareSpectator(PlayerId playerId, Match match, ArenaDefinition arenaDefinition);

    /**
     * Clears match-specific adapter state for the player.
     *
     * <p>Implementations must treat this as idempotent because match start rollback and completed-match
     * teardown retries can replay cleanup after a prior partial success.
     */
    void clearMatchState(PlayerId playerId);
}
