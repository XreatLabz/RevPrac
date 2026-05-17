package io.github.xreatlabz.revprac.ports.recovery;

import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.players.PendingRestoration;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSession;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RuntimeRecoveryRepository {

    List<PlayerSession> playerSessions();

    void savePlayerSession(PlayerSession session);

    void deletePlayerSession(PlayerId playerId);

    List<PendingRestoration> pendingRestorations();

    void savePendingRestoration(PendingRestoration restoration);

    void deletePendingRestoration(PlayerId playerId);

    List<QueueTicket> queueTickets();

    Optional<QueueTicket> queueTicket(PlayerId playerId);

    void saveQueueTicket(QueueTicket ticket, Instant joinedAt);

    void deleteQueueTicket(QueueTicketId ticketId);

    void deleteQueueTicketByPlayer(PlayerId playerId);

    List<Match> matches();

    void saveMatch(Match match);

    void deleteMatch(MatchId matchId);
}
