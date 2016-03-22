package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "log_book_request")
public class LogBookRequest extends AbstractLogBookEntity {
	@Column(name = "replicate_to_subgroups", nullable = false)
	private boolean replicateToSubgroups;

	private LogBookRequest() {
		// for JPA
	}

	public static LogBookRequest makeEmpty(User createdByUser, Group group) {
		LogBookRequest request = new LogBookRequest();
		request.uid = UIDGenerator.generateId();
		request.createdByUser = createdByUser;
		request.group = group;

		return request;
	}

	public boolean isReplicateToSubgroups() {
		return replicateToSubgroups;
	}

	public void setReplicateToSubgroups(boolean replicateToSubgroups) {
		this.replicateToSubgroups = replicateToSubgroups;
	}
}
