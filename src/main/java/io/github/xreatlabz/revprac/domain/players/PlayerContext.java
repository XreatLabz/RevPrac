package io.github.xreatlabz.revprac.domain.players;

public enum PlayerContext {
    LOBBY,
    QUEUE,
    MATCH,
    SPECTATOR,
    EDITOR;

    public boolean isManaged() {
        return this != LOBBY;
    }
}
