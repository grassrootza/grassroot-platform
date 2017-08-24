package za.org.grassroot.core.domain.task;

import za.org.grassroot.core.domain.UidIdentifiable;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.AbstractEventEntity;
import za.org.grassroot.core.enums.EventType;

import javax.persistence.*;
import java.util.HashSet;
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

	protected boolean isFilledWithCommonFields() {
		return !(getName() == null || getName().trim().equals("")) && getCreatedByUser() != null && getEventStartDateTime() != null;
	}
}
