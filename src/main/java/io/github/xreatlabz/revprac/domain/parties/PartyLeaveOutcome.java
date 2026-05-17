package io.github.xreatlabz.revprac.domain.parties;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.Objects;
import java.util.Optional;

public record PartyLeaveOutcome(
        PartyId partyId,
        Optional<Party> updatedParty,
        Optional<PlayerId> promotedLeaderId) {

    public PartyLeaveOutcome {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(updatedParty, "updatedParty");
        Objects.requireNonNull(promotedLeaderId, "promotedLeaderId");
        if (updatedParty.isEmpty() && promotedLeaderId.isPresent()) {
            throw new IllegalArgumentException("disbanded parties must not promote a leader");
        }
        if (updatedParty.isPresent()) {
            Party party = updatedParty.orElseThrow();
            if (!party.id().equals(partyId)) {
                throw new IllegalArgumentException("updated party must keep the same id");
            }
            if (promotedLeaderId.isPresent() && !party.leaderId().equals(promotedLeaderId.orElseThrow())) {
                throw new IllegalArgumentException("promoted leader must match the updated party leader");
            }
        }
    }

    public static PartyLeaveOutcome disbanded(PartyId partyId) {
        return new PartyLeaveOutcome(partyId, Optional.empty(), Optional.empty());
    }

    public static PartyLeaveOutcome updated(Party party, Optional<PlayerId> promotedLeaderId) {
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(promotedLeaderId, "promotedLeaderId");
        return new PartyLeaveOutcome(party.id(), Optional.of(party), promotedLeaderId);
    }

    public boolean disbanded() {
        return updatedParty.isEmpty();
    }

    public Optional<PartyStatus> statusAfterLeave() {
        return updatedParty.map(Party::status);
    }
}
