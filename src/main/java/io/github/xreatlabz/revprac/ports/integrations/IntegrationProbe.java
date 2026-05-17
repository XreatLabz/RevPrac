package io.github.xreatlabz.revprac.ports.integrations;

import io.github.xreatlabz.revprac.application.integrations.IntegrationStatus;
import java.util.List;

public interface IntegrationProbe {

    List<IntegrationStatus> statuses();
}
