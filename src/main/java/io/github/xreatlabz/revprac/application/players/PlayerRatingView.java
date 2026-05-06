package io.github.xreatlabz.revprac.application.players;

public record PlayerRatingView(int rating) {

    public PlayerRatingView {
        if (rating <= 0) {
            throw new IllegalArgumentException("rating must be positive");
        }
    }
}
