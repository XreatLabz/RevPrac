package io.github.xreatlabz.revprac.application.integrations;

import java.util.Objects;

public record IntegrationStatus(IntegrationType type, String pluginName, boolean present) {

    public IntegrationStatus {
        type = Objects.requireNonNull(type, "type");
        pluginName = Objects.requireNonNull(pluginName, "pluginName").trim();
        if (pluginName.isEmpty()) {
            throw new IllegalArgumentException("pluginName must not be blank");
        }
    }
}
