package io.github.xreatlabz.revprac.application.result;

public record Err<T>(Problem problem) implements Result<T> {
}
