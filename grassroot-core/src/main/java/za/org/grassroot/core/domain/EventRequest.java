package za.org.grassroot.core.domain;

import javax.persistence.*;
import java.sql.Timestamp;

@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class EventRequest extends AbstractEventContent {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "uid", length = 50)
	private String uid;

	@Column(name = "created_date_time", insertable = true, updatable = false)
	private Timestamp createdDateTime;

	/*
	Used to prevent a formed entity from sending out when on the confirm screen of USSD
	 */
	@Column(name = "send_blocked", nullable = false)
	private boolean sendBlocked;

	protected EventRequest() {
		// for JPA
	}

	public boolean isSendBlocked() {
		return sendBlocked;
	}

	public void setSendBlocked(boolean sendBlocked) {
		this.sendBlocked = sendBlocked;
	}
}
