package io.github.xreatlabz.revprac.adapters.paper;

import io.github.xreatlabz.revprac.application.result.Problem;
import io.github.xreatlabz.revprac.ports.lifecycle.LifecycleReporter;
import java.util.Objects;
import java.util.logging.Logger;

public final class PaperLifecycleReporter implements LifecycleReporter {

    private final Logger logger;

    public PaperLifecycleReporter(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void startupFailed(Problem problem) {
        logger.severe("RevPrac bootstrap failed [" + problem.code() + "] at " + problem.path() + ": " + problem.message());
    }
}
