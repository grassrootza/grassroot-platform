package za.org.grassroot.core.domain;

import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.enums.TodoLogType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Created by aakilomar on 12/3/15.
 */
@Entity
@Table(name = "action_todo_log",
		indexes = {@Index(name = "idx_action_todo_log_actiontodo_id", columnList = "action_todo_id")})
public class TodoLog implements TaskLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "uid", nullable = false, unique = true)
	private String uid;

	@Basic
	@Column(name = "created_date_time", nullable = false, updatable = false)
	private Instant createdDateTime;

	@ManyToOne
	@JoinColumn(name = "action_todo_id", nullable = false)
	private Todo todo;

	@Column(name = "message")
	private String message;

	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name="type", nullable = false, length = 50)
 	private TodoLogType type;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name="latitude", column = @Column(nullable = true)),
			@AttributeOverride(name="longitude", column = @Column(nullable = true))
	})
	private GeoLocation location;

	private TodoLog() {
		// for JPA only
	}

	public TodoLog(TodoLogType type, User user, Todo todo, String message) {
		this.uid = UIDGenerator.generateId();
		this.type = Objects.requireNonNull(type);
		this.todo = Objects.requireNonNull(todo);
		this.message = message;
		this.user = user;
		this.createdDateTime = Instant.now();
	}

	public String getUid() { return uid; }

	public TodoLogType getType() {
		return type;
	}

	public Long getId() {
		return id;
	}

	public Instant getCreatedDateTime() {
		return createdDateTime;
	}

	public Todo getTodo() {
		return todo;
	}

	public User getUser() {
		return user;
	}

	@Override
	public Task getTask() {
		return todo;
	}

	public String getMessage() {
		return message;
	}

	public GeoLocation getLocation() {
		return location;
	}

	public void setLocation(GeoLocation location) {
		this.location = location;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("TodoLog{");
		sb.append("type=").append(type);
		sb.append(", todo=").append(todo);
		sb.append(", user=").append(user);
		sb.append(", createdDateTime=").append(createdDateTime);
		sb.append(", id=").append(id);
		sb.append('}');
		return sb.toString();
	}
}
