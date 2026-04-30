package io.github.xreatlabz.revprac.domain.arenas;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record ArenaId(String value) {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,62}");

    public ArenaId {
        Objects.requireNonNull(value, "value");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("arena id must match " + ID_PATTERN.pattern());
        }
    }
}
