package io.github.xreatlabz.revprac.application.operations;

@FunctionalInterface
public interface RegistryLoader<T> {

    T load() throws Exception;
}
