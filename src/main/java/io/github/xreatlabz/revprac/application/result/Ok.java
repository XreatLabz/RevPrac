package io.github.xreatlabz.revprac.application.result;

public record Ok<T>(T value) implements Result<T> {
}
