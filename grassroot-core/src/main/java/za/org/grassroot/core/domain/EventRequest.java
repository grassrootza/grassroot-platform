package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.EventType;

import javax.persistence.*;
import java.time.LocalTime;

@Entity
@Table(name = "event_request")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class EventRequest extends AbstractEventEntity {
	public abstract EventType getEventType();

	public abstract boolean isFilled();

	protected EventRequest() {
		// for JPA
	}

	protected boolean isFilledWithCommonFields() {
		if (getName() == null || getName().trim().equals("")) {
			return false;
		}
		if (getAppliesToGroup() == null) {
			return false;
		}
		if (getCreatedByUser() == null) {
			return false;
		}
		if (getEventStartDateTime() == null) {
			return false;
		}
		return true;
	}
}
