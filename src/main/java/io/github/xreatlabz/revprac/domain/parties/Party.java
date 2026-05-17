package io.github.xreatlabz.revprac.domain.parties;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record Party(PartyId id, PlayerId leaderId, List<PlayerId> members) {

    public Party {
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

    public static Party create(PartyId id, PlayerId leaderId) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(leaderId, "leaderId");
        return new Party(id, leaderId, List.of(leaderId));
    }

    public boolean contains(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return members.contains(playerId);
    }

    public Party join(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (contains(playerId)) {
            throw new IllegalStateException("player is already in the party");
        }
        List<PlayerId> updatedMembers = new ArrayList<>(members);
        updatedMembers.add(playerId);
        return new Party(id, leaderId, updatedMembers);
    }

    public PartyLeaveOutcome leave(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!contains(playerId)) {
            throw new IllegalStateException("player is not in the party");
        }
        if (members.size() == 1) {
            return PartyLeaveOutcome.disbanded(id);
        }
        List<PlayerId> updatedMembers = new ArrayList<>(members);
        updatedMembers.remove(playerId);
        PlayerId nextLeader = leaderId.equals(playerId) ? updatedMembers.get(0) : leaderId;
        Party updatedParty = new Party(id, nextLeader, updatedMembers);
        Optional<PlayerId> promotedLeaderId = leaderId.equals(nextLeader) ? Optional.empty() : Optional.of(nextLeader);
        return PartyLeaveOutcome.updated(updatedParty, promotedLeaderId);
    }

    public PartyStatus status() {
        return new PartyStatus(id, leaderId, members);
    }

    public PartyQueueEligibilitySnapshot queueEligibility(int requiredSize) {
        if (requiredSize <= 0) {
            throw new IllegalArgumentException("requiredSize must be positive");
        }
        if (members.size() == requiredSize) {
            return new PartyQueueEligibilitySnapshot(id, leaderId, members, requiredSize, true, Optional.empty());
        }
        return new PartyQueueEligibilitySnapshot(
                id,
                leaderId,
                members,
                requiredSize,
                false,
                Optional.of("party size " + members.size() + " does not match required size " + requiredSize));
    }
}
