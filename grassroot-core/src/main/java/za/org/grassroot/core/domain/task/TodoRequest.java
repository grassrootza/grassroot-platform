package za.org.grassroot.core.domain.task;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "action_todo_request")
public class TodoRequest extends AbstractTodoEntity {
	@Column(name = "replicate_to_subgroups", nullable = false)
	private boolean replicateToSubgroups;

	@ManyToMany(cascade = CascadeType.ALL)
	@JoinTable(name = "action_todo_request_assigned_members",
			joinColumns = @JoinColumn(name = "action_todo_request_id", nullable = false),
			inverseJoinColumns = @JoinColumn(name = "user_id", nullable = false))
	private Set<User> assignedMembers = new HashSet<>();

	private TodoRequest() {
		// for JPA
	}

	public static TodoRequest makeEmpty(User createdByUser, TodoContainer parent) {
		TodoRequest request = new TodoRequest();
		request.uid = UIDGenerator.generateId();
		request.createdByUser = createdByUser;
		request.setParent(parent);
		return request;
	}

	public static TodoRequest makeEmpty(User createdByUser) {
		TodoRequest request = new TodoRequest();
		request.uid = UIDGenerator.generateId();
		request.createdByUser = createdByUser;
		return request;
	}

	public boolean isReplicateToSubgroups() {
		return replicateToSubgroups;
	}

	public void setReplicateToSubgroups(boolean replicateToSubgroups) {
		this.replicateToSubgroups = replicateToSubgroups;
	}

	public Set<User> getAssignedMembers() {
		if (assignedMembers == null) {
			assignedMembers = new HashSet<>();
		}
		return new HashSet<>(assignedMembers);
	}
}
