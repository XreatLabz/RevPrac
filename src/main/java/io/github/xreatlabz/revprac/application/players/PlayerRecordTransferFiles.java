package io.github.xreatlabz.revprac.application.players;

public interface PlayerRecordTransferFiles {

    String export(PlayerRecordBundle bundle);

    PlayerRecordBundle importFromFile(String simpleFileName);
}
