package io.github.xreatlabz.revprac.adapters.storage.jdbc;

import io.github.xreatlabz.revprac.application.operations.AuditEntry;
import io.github.xreatlabz.revprac.ports.operations.AuditRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcAuditRepository implements AuditRepository {

    private final DataSource dataSource;

    public JdbcAuditRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void append(AuditEntry entry) {
        Objects.requireNonNull(entry, "entry");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "insert into audit_log (audit_id, occurred_at, actor, action, details) values (?, ?, ?, ?, ?)")) {
            statement.setString(1, entry.id().toString());
            statement.setLong(2, entry.occurredAt().toEpochMilli());
            statement.setString(3, entry.actor());
            statement.setString(4, entry.action());
            statement.setString(5, entry.details());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to append audit entry " + entry.id(), exception);
        }
    }

    @Override
    public List<AuditEntry> recent(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select audit_id, occurred_at, actor, action, details "
                                + "from audit_log order by occurred_at desc, audit_id desc limit ?")) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<AuditEntry> entries = new ArrayList<>();
                while (resultSet.next()) {
                    entries.add(new AuditEntry(
                            UUID.fromString(resultSet.getString("audit_id")),
                            Instant.ofEpochMilli(resultSet.getLong("occurred_at")),
                            resultSet.getString("actor"),
                            resultSet.getString("action"),
                            resultSet.getString("details")));
                }
                return List.copyOf(entries);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load recent audit entries", exception);
        }
    }
}
