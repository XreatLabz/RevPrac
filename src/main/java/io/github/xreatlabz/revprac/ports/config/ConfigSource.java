package io.github.xreatlabz.revprac.ports.config;

public interface ConfigSource {

    Object rawValue(String path);

    boolean hasPath(String path);

    String sourceDescription();
}
