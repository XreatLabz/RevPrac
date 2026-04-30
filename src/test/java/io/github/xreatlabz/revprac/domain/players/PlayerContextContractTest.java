package io.github.xreatlabz.revprac.domain.players;

import static io.github.xreatlabz.revprac.ContractTestSupport.loadClass;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PlayerContextContractTest {

    private static final String PLAYER_CONTEXT_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerContext";
    private static final String TRANSITION_REASON_TYPE = "io.github.xreatlabz.revprac.domain.players.TransitionReason";

    @Test
    void playerContextEnumDeclaresOnlyDocumentedContexts() {
        Class<?> playerContextType = loadClass(PLAYER_CONTEXT_TYPE);

        assertTrue(playerContextType.isEnum(), "PlayerContext should be an enum");
        assertArrayEquals(
                new String[] {"LOBBY", "QUEUE", "MATCH", "SPECTATOR", "EDITOR"},
                enumNames(playerContextType));
    }

    @Test
    void transitionReasonEnumDeclaresOnlyDocumentedReasons() {
        Class<?> transitionReasonType = loadClass(TRANSITION_REASON_TYPE);

        assertTrue(transitionReasonType.isEnum(), "TransitionReason should be an enum");
        assertArrayEquals(
                new String[] {
                    "JOIN",
                    "QUEUE_JOIN",
                    "MATCH_START",
                    "SPECTATE",
                    "EDITOR_OPEN",
                    "RETURN_TO_LOBBY",
                    "QUIT",
                    "PLUGIN_DISABLE"
                },
                enumNames(transitionReasonType));
    }

    private static String[] enumNames(Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        String[] names = new String[constants.length];
        for (int index = 0; index < constants.length; index++) {
            names[index] = ((Enum<?>) constants[index]).name();
        }
        return names;
    }
}
