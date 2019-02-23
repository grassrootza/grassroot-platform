package za.org.grassroot.core.domain.task;

import za.org.grassroot.core.domain.UidIdentifiable;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;

import javax.persistence.CascadeType;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Table;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "event_request")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class EventRequest<P extends UidIdentifiable> extends AbstractEventEntity {

	@ManyToMany(cascade = CascadeType.ALL)
	@JoinTable(name = "event_request_assigned_members",
			joinColumns = @JoinColumn(name = "event_request_id", nullable = false),
			inverseJoinColumns = @JoinColumn(name = "user_id", nullable = false)
	)
	private Set<User> assignedMembers = new HashSet<>();

	public Set<User> getAssignedMembers() {
		if (assignedMembers == null) {
			assignedMembers = new HashSet<>();
		}
		return new HashSet<>(assignedMembers);
	}

	public abstract EventType getEventType();

	public abstract boolean isFilled();

	protected EventRequest() {
		// for JPA
	}

	public List<String> getVoteOptions() {
		return getTagList();
	}

	protected boolean isFilledWithCommonFields() {
		return !(getName() == null || getName().trim().equals("")) && getCreatedByUser() != null && getEventStartDateTime() != null;
	}

}
