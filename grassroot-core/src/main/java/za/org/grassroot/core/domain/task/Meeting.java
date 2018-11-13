package za.org.grassroot.core.domain.task;

import org.apache.commons.lang3.StringUtils;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.enums.EventSpecialForm;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.time.Instant;
import java.util.Objects;

@Entity
@DiscriminatorValue("MEETING")
public class Meeting extends Event<MeetingContainer> implements VoteContainer {

	@Column(name = "location")
	private String eventLocation;

	private Meeting() {
		// for JPA
	}

	public Meeting(String name, Instant startDateTime, User user, MeetingContainer parent, String eventLocation) {
		this(name, startDateTime, user, parent, eventLocation, false);
	}

	public Meeting(String name, Instant startDateTime, User user, MeetingContainer parent, String eventLocation, boolean includeSubGroups) {
		this(name, startDateTime, user, parent, eventLocation, includeSubGroups, EventReminderType.DISABLED, 0, null, null);
	}

	// production constructor : above are only used in tests
	private Meeting(String name, Instant startDateTime, User user, MeetingContainer parent, String eventLocation, boolean includeSubGroups,
				   EventReminderType reminderType, int customReminderMinutes, String description, EventSpecialForm importance) {
		super(startDateTime, user, parent, name, includeSubGroups, reminderType, customReminderMinutes, description, true, false);
		this.eventLocation = Objects.requireNonNull(eventLocation);
		this.specialForm = importance == null ? EventSpecialForm.ORDINARY : importance;
		setScheduledReminderActive(true);
		setParent(parent);
	}

	public static Meeting makeEmpty(User user) {
		Meeting meeting = new MeetingBuilder().createMeeting();
		meeting.uid = UIDGenerator.generateId();
		meeting.setCreatedByUser(user);
		return meeting;
	}

	@Override
	public EventType getEventType() {
		return EventType.MEETING;
	}

    @Override
    public TaskType getTaskType() {
        return TaskType.MEETING;
    }

	@Override
	public JpaEntityType getJpaEntityType() {
		return JpaEntityType.MEETING;
	}

	public String getEventLocation() {
		return eventLocation;
	}

	public void setEventLocation(String eventLocation) {
		this.eventLocation = StringUtils.truncate(eventLocation, 50);
	}

	@Override
	public boolean isHighImportance() {
		return EventSpecialForm.IMPORTANT_MEETING.equals(specialForm);
	}

	public MeetingContainer getParent() {
		if (parentGroup != null) {
			return parentGroup;
		} else if (parentTodo != null) {
			return parentTodo;
		} else {
			throw new IllegalStateException("There is no " + MeetingContainer.class.getSimpleName() + " parent defined for " + this);
		}
	}

    public void setParent(MeetingContainer parent) {
		if (parent instanceof Group) {
			this.parentGroup = (Group) parent;
			this.parentGroup.addChildEvent(this); // needed for double sided relationship in JPA
		} else if (parent instanceof Todo) {
			this.parentTodo = (Todo) parent;
		} else {
			throw new UnsupportedOperationException("Unsupported parent: " + parent);
		}
	}
}
