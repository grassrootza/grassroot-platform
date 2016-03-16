package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.EventType;

import javax.persistence.*;

@Entity
@Table(name = "event_request")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class EventRequest extends AbstractEventEntity {
	/*
	Used to prevent a formed entity from sending out when on the confirm screen of USSD
	 */
	@Column(name = "send_blocked", nullable = false)
	protected boolean sendBlocked;

	public abstract EventType getEventType();

	protected EventRequest() {
		// for JPA
	}

	public boolean isSendBlocked() {
		return sendBlocked;
	}

	public void setSendBlocked(boolean sendBlocked) {
		this.sendBlocked = sendBlocked;
	}

	public boolean minimumDataAvailable() {
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
