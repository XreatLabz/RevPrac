package io.github.xreatlabz.revprac.ports.lifecycle;

import io.github.xreatlabz.revprac.application.result.Problem;

public interface LifecycleReporter {

    void info(String message);

    void startupFailed(Problem problem);
}
