package io.github.xreatlabz.revprac.adapters.paper.arenas;

import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.ports.arenas.ArenaResetPort;
import java.util.Objects;
import java.util.logging.Logger;

public final class PaperArenaResetAdapter implements ArenaResetPort {

    private final Logger logger;

    public PaperArenaResetAdapter(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void reset(ArenaDefinition arenaDefinition) {
        Objects.requireNonNull(arenaDefinition, "arenaDefinition");
        logger.info("Arena reset requested for " + arenaDefinition.id().value()
                + "; Phase 3 does not apply block rollback yet.");
    }
}
