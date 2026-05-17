package io.github.xreatlabz.revprac.ports.operations;

import io.github.xreatlabz.revprac.application.operations.AuditEntry;
import java.util.List;

public interface AuditRepository {

    void append(AuditEntry entry);

    List<AuditEntry> recent(int limit);
}
