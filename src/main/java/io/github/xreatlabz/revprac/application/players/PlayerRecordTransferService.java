package io.github.xreatlabz.revprac.application.players;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.players.PlayerRecordTransferRepository;
import java.util.Objects;

public final class PlayerRecordTransferService {

    private final PlayerRecordTransferRepository playerRecordTransferRepository;
    private final PlayerRecordTransferFiles playerRecordTransferFiles;

    public PlayerRecordTransferService(
            PlayerRecordTransferRepository playerRecordTransferRepository,
            PlayerRecordTransferFiles playerRecordTransferFiles) {
        this.playerRecordTransferRepository =
                Objects.requireNonNull(playerRecordTransferRepository, "playerRecordTransferRepository");
        this.playerRecordTransferFiles = Objects.requireNonNull(playerRecordTransferFiles, "playerRecordTransferFiles");
    }

    public String export(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return playerRecordTransferFiles.export(playerRecordTransferRepository.exportBundle(playerId));
    }

    public PlayerRecordBundle importFromFile(String simpleFileName) {
        PlayerRecordBundle bundle = playerRecordTransferFiles.importFromFile(simpleFileName);
        playerRecordTransferRepository.importBundle(bundle);
        return bundle;
    }
}
