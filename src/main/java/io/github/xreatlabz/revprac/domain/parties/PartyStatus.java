package io.github.xreatlabz.revprac.domain.parties;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record PartyStatus(PartyId id, PlayerId leaderId, List<PlayerId> members) {

    public PartyStatus {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(leaderId, "leaderId");
        Objects.requireNonNull(members, "members");
        members = List.copyOf(members);
        if (members.isEmpty()) {
            throw new IllegalArgumentException("party members must not be empty");
        }
        Set<PlayerId> distinctMembers = new HashSet<>();
        for (PlayerId memberId : members) {
            Objects.requireNonNull(memberId, "memberId");
            if (!distinctMembers.add(memberId)) {
                throw new IllegalArgumentException("party members must be distinct");
            }
        }
        if (!members.contains(leaderId)) {
            throw new IllegalArgumentException("party leader must be a party member");
        }
    }

    public int memberCount() {
        return members.size();
    }

    public boolean contains(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return members.contains(playerId);
    }
}
