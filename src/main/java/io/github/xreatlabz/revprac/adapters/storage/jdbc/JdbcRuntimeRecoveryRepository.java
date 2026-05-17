package io.github.xreatlabz.revprac.adapters.storage.jdbc;

import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.matches.MatchOutcome;
import io.github.xreatlabz.revprac.domain.matches.MatchParticipants;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.matches.MatchState;
import io.github.xreatlabz.revprac.domain.players.InventorySnapshot;
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PendingRestoration;
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerSession;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.domain.players.PotionEffectSnapshot;
import io.github.xreatlabz.revprac.domain.players.TransitionReason;
import io.github.xreatlabz.revprac.domain.queues.QueueKey;
import io.github.xreatlabz.revprac.domain.queues.QueueMode;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketId;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketState;
import io.github.xreatlabz.revprac.ports.recovery.RuntimeRecoveryRepository;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcRuntimeRecoveryRepository implements RuntimeRecoveryRepository {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final DataSource dataSource;

    public JdbcRuntimeRecoveryRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public List<PlayerSession> playerSessions() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select player_id, context, snapshot from runtime_player_sessions order by player_id");
                ResultSet resultSet = statement.executeQuery()) {
            List<PlayerSession> sessions = new ArrayList<>();
            while (resultSet.next()) {
                sessions.add(new PlayerSession(
                        playerId(resultSet.getString("player_id")),
                        PlayerContext.valueOf(resultSet.getString("context")),
                        decodeSnapshot(resultSet.getString("snapshot"))));
            }
            return List.copyOf(sessions);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load runtime player sessions", exception);
        }
    }

    @Override
    public void savePlayerSession(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        if (!session.isManaged()) {
            deletePlayerSession(session.playerId());
            return;
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "insert into runtime_player_sessions (player_id, context, snapshot, updated_at) "
                                + "values (?, ?, ?, ?) "
                                + "on conflict(player_id) do update set "
                                + "context = excluded.context, snapshot = excluded.snapshot, updated_at = excluded.updated_at")) {
            statement.setString(1, session.playerId().value().toString());
            statement.setString(2, session.context().name());
            statement.setString(3, encodeSnapshot(session.returnSnapshot()));
            statement.setLong(4, Instant.now().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save runtime player session for " + session.playerId().value(), exception);
        }
    }

    @Override
    public void deletePlayerSession(PlayerId playerId) {
        deleteByPlayer("runtime_player_sessions", playerId);
    }

    @Override
    public List<PendingRestoration> pendingRestorations() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select player_id, reason, snapshot from runtime_pending_restorations order by player_id");
                ResultSet resultSet = statement.executeQuery()) {
            List<PendingRestoration> restorations = new ArrayList<>();
            while (resultSet.next()) {
                restorations.add(new PendingRestoration(
                        playerId(resultSet.getString("player_id")),
                        decodeSnapshot(resultSet.getString("snapshot")),
                        TransitionReason.valueOf(resultSet.getString("reason"))));
            }
            return List.copyOf(restorations);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load runtime pending restorations", exception);
        }
    }

    @Override
    public void savePendingRestoration(PendingRestoration restoration) {
        Objects.requireNonNull(restoration, "restoration");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "insert into runtime_pending_restorations (player_id, reason, snapshot, updated_at) "
                                + "values (?, ?, ?, ?) "
                                + "on conflict(player_id) do update set "
                                + "reason = excluded.reason, snapshot = excluded.snapshot, updated_at = excluded.updated_at")) {
            statement.setString(1, restoration.playerId().value().toString());
            statement.setString(2, restoration.reason().name());
            statement.setString(3, encodeSnapshot(restoration.snapshot()));
            statement.setLong(4, Instant.now().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to save runtime pending restoration for " + restoration.playerId().value(),
                    exception);
        }
    }

    @Override
    public void deletePendingRestoration(PlayerId playerId) {
        deleteByPlayer("runtime_pending_restorations", playerId);
    }

    @Override
    public List<QueueTicket> queueTickets() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select ticket_id, player_id, queue_mode, kit_id, joined_at_tick, search_rating, state "
                                + "from runtime_queue_tickets order by joined_at_epoch_millis, ticket_id");
                ResultSet resultSet = statement.executeQuery()) {
            List<QueueTicket> tickets = new ArrayList<>();
            while (resultSet.next()) {
                tickets.add(mapQueueTicket(resultSet));
            }
            return List.copyOf(tickets);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load runtime queue tickets", exception);
        }
    }

    @Override
    public Optional<QueueTicket> queueTicket(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select ticket_id, player_id, queue_mode, kit_id, joined_at_tick, search_rating, state "
                                + "from runtime_queue_tickets where player_id = ?")) {
            statement.setString(1, playerId.value().toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapQueueTicket(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load runtime queue ticket for " + playerId.value(), exception);
        }
    }

    @Override
    public void saveQueueTicket(QueueTicket ticket, Instant joinedAt) {
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(joinedAt, "joinedAt");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "insert into runtime_queue_tickets "
                                + "(ticket_id, player_id, queue_mode, kit_id, joined_at_tick, joined_at_epoch_millis, search_rating, state) "
                                + "values (?, ?, ?, ?, ?, ?, ?, ?) "
                                + "on conflict(ticket_id) do update set "
                                + "player_id = excluded.player_id, queue_mode = excluded.queue_mode, kit_id = excluded.kit_id, "
                                + "joined_at_tick = excluded.joined_at_tick, joined_at_epoch_millis = excluded.joined_at_epoch_millis, "
                                + "search_rating = excluded.search_rating, state = excluded.state")) {
            statement.setString(1, ticket.id().value().toString());
            statement.setString(2, ticket.playerId().value().toString());
            statement.setString(3, ticket.key().mode().name());
            statement.setString(4, ticket.key().kitId().value());
            statement.setLong(5, ticket.joinedAtTick());
            statement.setLong(6, joinedAt.toEpochMilli());
            statement.setInt(7, ticket.searchRating());
            statement.setString(8, ticket.state().name());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save runtime queue ticket " + ticket.id().value(), exception);
        }
    }

    @Override
    public void deleteQueueTicket(QueueTicketId ticketId) {
        Objects.requireNonNull(ticketId, "ticketId");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "delete from runtime_queue_tickets where ticket_id = ?")) {
            statement.setString(1, ticketId.value().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete runtime queue ticket " + ticketId.value(), exception);
        }
    }

    @Override
    public void deleteQueueTicketByPlayer(PlayerId playerId) {
        deleteByPlayer("runtime_queue_tickets", playerId);
    }

    @Override
    public List<Match> matches() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select match_id, player_one_id, player_two_id, arena_id, kit_id, match_origin, arena_reservation_id, "
                                + "countdown_ticks, max_duration_ticks, spectators_enabled, match_state, "
                                + "countdown_ticks_remaining, active_ticks_elapsed, outcome_reason, winner_id, loser_id, completed_at "
                                + "from runtime_matches order by updated_at, match_id");
                ResultSet resultSet = statement.executeQuery()) {
            List<Match> matches = new ArrayList<>();
            while (resultSet.next()) {
                matches.add(mapMatch(resultSet));
            }
            return List.copyOf(matches);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load runtime matches", exception);
        }
    }

    @Override
    public void saveMatch(Match match) {
        Objects.requireNonNull(match, "match");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "insert into runtime_matches "
                                + "(match_id, player_one_id, player_two_id, arena_id, kit_id, match_origin, arena_reservation_id, "
                                + "countdown_ticks, max_duration_ticks, spectators_enabled, match_state, countdown_ticks_remaining, "
                                + "active_ticks_elapsed, outcome_reason, winner_id, loser_id, completed_at, updated_at) "
                                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                                + "on conflict(match_id) do update set "
                                + "player_one_id = excluded.player_one_id, player_two_id = excluded.player_two_id, "
                                + "arena_id = excluded.arena_id, kit_id = excluded.kit_id, match_origin = excluded.match_origin, "
                                + "arena_reservation_id = excluded.arena_reservation_id, countdown_ticks = excluded.countdown_ticks, "
                                + "max_duration_ticks = excluded.max_duration_ticks, spectators_enabled = excluded.spectators_enabled, "
                                + "match_state = excluded.match_state, countdown_ticks_remaining = excluded.countdown_ticks_remaining, "
                                + "active_ticks_elapsed = excluded.active_ticks_elapsed, outcome_reason = excluded.outcome_reason, "
                                + "winner_id = excluded.winner_id, loser_id = excluded.loser_id, completed_at = excluded.completed_at, "
                                + "updated_at = excluded.updated_at")) {
            statement.setString(1, match.id().value().toString());
            statement.setString(2, match.participants().playerOne().value().toString());
            statement.setString(3, match.participants().playerTwo().value().toString());
            statement.setString(4, match.arenaId().value());
            statement.setString(5, match.kitId().value());
            statement.setString(6, match.origin().name());
            statement.setString(7, match.arenaReservationId().value().toString());
            statement.setInt(8, match.ruleset().countdownTicks());
            statement.setInt(9, match.ruleset().maxDurationTicks());
            statement.setBoolean(10, match.ruleset().spectatorsEnabled());
            statement.setString(11, match.state().name());
            statement.setInt(12, match.countdownTicksRemaining());
            statement.setInt(13, match.activeTicksElapsed());
            statement.setString(14, match.outcome().map(value -> value.reason().name()).orElse(null));
            statement.setString(15, match.outcome()
                    .flatMap(MatchOutcome::winnerId)
                    .map(PlayerId::value)
                    .map(UUID::toString)
                    .orElse(null));
            statement.setString(16, match.outcome()
                    .flatMap(MatchOutcome::loserId)
                    .map(PlayerId::value)
                    .map(UUID::toString)
                    .orElse(null));
            if (match.completedAt().isPresent()) {
                statement.setLong(17, match.completedAt().orElseThrow().toEpochMilli());
            } else {
                statement.setObject(17, null);
            }
            statement.setLong(18, Instant.now().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save runtime match " + match.id().value(), exception);
        }
    }

    @Override
    public void deleteMatch(MatchId matchId) {
        Objects.requireNonNull(matchId, "matchId");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "delete from runtime_matches where match_id = ?")) {
            statement.setString(1, matchId.value().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete runtime match " + matchId.value(), exception);
        }
    }

    private static QueueTicket mapQueueTicket(ResultSet resultSet) throws SQLException {
        return new QueueTicket(
                new QueueTicketId(UUID.fromString(resultSet.getString("ticket_id"))),
                playerId(resultSet.getString("player_id")),
                new QueueKey(QueueMode.valueOf(resultSet.getString("queue_mode")), new KitId(resultSet.getString("kit_id"))),
                resultSet.getLong("joined_at_tick"),
                resultSet.getInt("search_rating"),
                QueueTicketState.valueOf(resultSet.getString("state")));
    }

    private static Match mapMatch(ResultSet resultSet) throws SQLException {
        Optional<MatchOutcome> outcome = outcome(resultSet);
        Long completedAt = nullableLong(resultSet, "completed_at");
        return new Match(
                new MatchId(UUID.fromString(resultSet.getString("match_id"))),
                new MatchParticipants(
                        playerId(resultSet.getString("player_one_id")),
                        playerId(resultSet.getString("player_two_id"))),
                new ArenaId(resultSet.getString("arena_id")),
                new KitId(resultSet.getString("kit_id")),
                MatchOrigin.valueOf(resultSet.getString("match_origin")),
                new ArenaReservationId(UUID.fromString(resultSet.getString("arena_reservation_id"))),
                new MatchRuleset(
                        resultSet.getInt("countdown_ticks"),
                        resultSet.getInt("max_duration_ticks"),
                        resultSet.getBoolean("spectators_enabled")),
                MatchState.valueOf(resultSet.getString("match_state")),
                resultSet.getInt("countdown_ticks_remaining"),
                resultSet.getInt("active_ticks_elapsed"),
                Set.of(),
                outcome,
                completedAt == null ? Optional.empty() : Optional.of(Instant.ofEpochMilli(completedAt)));
    }

    private static Optional<MatchOutcome> outcome(ResultSet resultSet) throws SQLException {
        String reason = resultSet.getString("outcome_reason");
        if (reason == null) {
            return Optional.empty();
        }
        MatchEndReason endReason = MatchEndReason.valueOf(reason);
        if (endReason == MatchEndReason.TIMEOUT) {
            return Optional.of(MatchOutcome.timeout());
        }
        if (endReason == MatchEndReason.SHUTDOWN) {
            return Optional.of(MatchOutcome.shutdown());
        }
        return Optional.of(new MatchOutcome(
                endReason,
                Optional.of(playerId(resultSet.getString("winner_id"))),
                Optional.of(playerId(resultSet.getString("loser_id")))));
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static PlayerId playerId(String value) {
        return new PlayerId(UUID.fromString(value));
    }

    private void deleteByPlayer(String tableName, PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "delete from " + tableName + " where player_id = ?")) {
            statement.setString(1, playerId.value().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete " + tableName + " row for " + playerId.value(), exception);
        }
    }

    private static String encodeSnapshot(PlayerSafetySnapshot snapshot) {
        Properties properties = new Properties();
        properties.setProperty("location.world", snapshot.location().worldKey());
        properties.setProperty("location.x", Double.toString(snapshot.location().x()));
        properties.setProperty("location.y", Double.toString(snapshot.location().y()));
        properties.setProperty("location.z", Double.toString(snapshot.location().z()));
        properties.setProperty("location.yaw", Float.toString(snapshot.location().yaw()));
        properties.setProperty("location.pitch", Float.toString(snapshot.location().pitch()));
        properties.setProperty("inventory.storage", encodeNullableList(snapshot.inventory().storage()));
        properties.setProperty("inventory.armor", encodeNullableList(snapshot.inventory().armor()));
        properties.setProperty("inventory.extra", encodeNullableList(snapshot.inventory().extra()));
        properties.setProperty("inventory.enderChest", encodeNullableList(snapshot.inventory().enderChest()));
        properties.setProperty("inventory.cursor", encodeNullable(snapshot.inventory().cursorItem()));
        properties.setProperty("inventory.selectedSlot", Integer.toString(snapshot.inventory().selectedSlot()));
        properties.setProperty("status.gameMode", snapshot.status().gameMode());
        properties.setProperty("status.health", Double.toString(snapshot.status().health()));
        properties.setProperty("status.foodLevel", Integer.toString(snapshot.status().foodLevel()));
        properties.setProperty("status.saturation", Float.toString(snapshot.status().saturation()));
        properties.setProperty("status.expProgress", Float.toString(snapshot.status().expProgress()));
        properties.setProperty("status.level", Integer.toString(snapshot.status().level()));
        properties.setProperty("status.allowFlight", Boolean.toString(snapshot.status().allowFlight()));
        properties.setProperty("status.flying", Boolean.toString(snapshot.status().flying()));
        properties.setProperty("status.effects", encodeEffects(snapshot.status().potionEffects()));
        try (StringWriter writer = new StringWriter()) {
            properties.store(writer, null);
            return writer.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode runtime recovery snapshot", exception);
        }
    }

    private static PlayerSafetySnapshot decodeSnapshot(String encoded) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(encoded));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to decode runtime recovery snapshot", exception);
        }
        LocationSnapshot location = new LocationSnapshot(
                properties.getProperty("location.world"),
                Double.parseDouble(properties.getProperty("location.x")),
                Double.parseDouble(properties.getProperty("location.y")),
                Double.parseDouble(properties.getProperty("location.z")),
                Float.parseFloat(properties.getProperty("location.yaw")),
                Float.parseFloat(properties.getProperty("location.pitch")));
        InventorySnapshot inventory = new InventorySnapshot(
                decodeNullableList(properties.getProperty("inventory.storage")),
                decodeNullableList(properties.getProperty("inventory.armor")),
                decodeNullableList(properties.getProperty("inventory.extra")),
                decodeNullableList(properties.getProperty("inventory.enderChest")),
                decodeNullable(properties.getProperty("inventory.cursor")),
                Integer.parseInt(properties.getProperty("inventory.selectedSlot")));
        PlayerStatusSnapshot status = new PlayerStatusSnapshot(
                properties.getProperty("status.gameMode"),
                Double.parseDouble(properties.getProperty("status.health")),
                Integer.parseInt(properties.getProperty("status.foodLevel")),
                Float.parseFloat(properties.getProperty("status.saturation")),
                Float.parseFloat(properties.getProperty("status.expProgress")),
                Integer.parseInt(properties.getProperty("status.level")),
                Boolean.parseBoolean(properties.getProperty("status.allowFlight")),
                Boolean.parseBoolean(properties.getProperty("status.flying")),
                decodeEffects(properties.getProperty("status.effects")));
        return new PlayerSafetySnapshot(location, inventory, status);
    }

    private static String encodeNullableList(List<String> values) {
        return values.stream().map(JdbcRuntimeRecoveryRepository::encodeNullable).reduce((a, b) -> a + "," + b).orElse("");
    }

    private static List<String> decodeNullableList(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : encoded.split(",", -1)) {
            values.add(decodeNullable(part));
        }
        return values;
    }

    private static String encodeNullable(String value) {
        return value == null ? "~" : "v" + ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeNullable(String value) {
        if ("~".equals(value)) {
            return null;
        }
        if (value == null || !value.startsWith("v")) {
            throw new IllegalStateException("Invalid encoded snapshot value");
        }
        return new String(DECODER.decode(value.substring(1)), StandardCharsets.UTF_8);
    }

    private static String encodeEffects(List<PotionEffectSnapshot> effects) {
        List<String> encoded = new ArrayList<>();
        for (PotionEffectSnapshot effect : effects) {
            encoded.add(encodeNullable(effect.effectKey())
                    + "|"
                    + effect.durationTicks()
                    + "|"
                    + effect.amplifier()
                    + "|"
                    + effect.ambient()
                    + "|"
                    + effect.particles()
                    + "|"
                    + effect.icon());
        }
        return String.join(",", encoded);
    }

    private static List<PotionEffectSnapshot> decodeEffects(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return List.of();
        }
        List<PotionEffectSnapshot> effects = new ArrayList<>();
        for (String effect : encoded.split(",", -1)) {
            String[] parts = effect.split("\\|", -1);
            if (parts.length != 6) {
                throw new IllegalStateException("Invalid encoded potion effect");
            }
            effects.add(new PotionEffectSnapshot(
                    Objects.requireNonNull(decodeNullable(parts[0])),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Boolean.parseBoolean(parts[3]),
                    Boolean.parseBoolean(parts[4]),
                    Boolean.parseBoolean(parts[5])));
        }
        return List.copyOf(effects);
    }
}
