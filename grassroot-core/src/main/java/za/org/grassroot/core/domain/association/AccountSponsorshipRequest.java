package za.org.grassroot.core.domain.association;


import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AssociationRequestType;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.persistence.*;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Request for joining the group.
 * We currently don't have child events here, because every collection association is potential burden,
 * and since it is planned to be rarely needed, we only define their relationship from event side.
 */
@Entity
@Table(name = "acc_sponsor_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_acc_sponsor_request_uid", columnNames = "uid"))
public class AccountSponsorshipRequest extends AbstractAssociationRequest<Account, User> {

    @ManyToOne
    @JoinColumn(name = "requestor_id", nullable = false, foreignKey = @ForeignKey(name = "fk_acc_sponsor_requestor"))
    private Account requestor;

    @ManyToOne
    @JoinColumn(name = "destination_id", nullable = false, foreignKey = @ForeignKey(name = "fk_acc_sponsor_destination"))
    private User destination;

    private AccountSponsorshipRequest() {
        // for JPA
    }

    public AccountSponsorshipRequest(Account requestor, User destination, String description) {
        super(description);
        this.requestor = Objects.requireNonNull(requestor);
        this.destination = Objects.requireNonNull(destination);
    }

    public Account getRequestor() {
        return requestor;
    }

    public User getDestination() {
        return destination;
    }

    public AssociationRequestType getType() { return AssociationRequestType.ACCOUNT_SPONSOR; }

    public ZonedDateTime getCreationTimeAtSAST() { return creationTime.atZone(DateTimeUtil.getSAST()); }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccountSponsorshipRequest)) {
            return false;
        }

        AccountSponsorshipRequest that = (AccountSponsorshipRequest) o;

        return getUid() != null ? getUid().equals(that.getUid()) : that.getUid() == null;
    }

    @Override
    public int hashCode() {
        return getUid() != null ? getUid().hashCode() : 0;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AccountSponsorRequest{");
        sb.append("id=").append(id);
        sb.append(", uid='").append(uid).append('\'');
        sb.append(", status=").append(status);
        sb.append(", requestorId=").append(requestor.getId());
        sb.append(", destinationId=").append(destination.getId());
        sb.append('}');
        return sb.toString();
    }
}
