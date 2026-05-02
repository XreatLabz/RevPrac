package io.github.xreatlabz.revprac.adapters.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.queues.QueueRatingRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class InMemoryQueueRatingRepositoryTest {

    @Test
    void missingRatingsReturnDefaultAndSavedRatingsAreScopedByPlayerAndKit() {
        QueueRatingRepository repository = new InMemoryQueueRatingRepository();
        PlayerId firstPlayer = player("first-player");
        PlayerId secondPlayer = player("second-player");
        KitId nodebuff = new KitId("nodebuff");
        KitId sumo = new KitId("sumo");

        assertEquals(1000, repository.rating(firstPlayer, nodebuff, 1000));

        repository.save(firstPlayer, nodebuff, 1125);

        assertEquals(1125, repository.rating(firstPlayer, nodebuff, 1000));
        assertEquals(1000, repository.rating(secondPlayer, nodebuff, 1000));
        assertEquals(1050, repository.rating(firstPlayer, sumo, 1050));
    }

    @Test
    void saveRejectsNonPositiveRatings() {
        QueueRatingRepository repository = new InMemoryQueueRatingRepository();
        PlayerId playerId = player("player");
        KitId kitId = new KitId("nodebuff");

        assertThrows(IllegalArgumentException.class, () -> repository.save(playerId, kitId, 0));
        assertThrows(IllegalArgumentException.class, () -> repository.save(playerId, kitId, -1));
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }
}
