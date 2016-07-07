package za.org.grassroot.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ComparisonChain;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.TodoStatus;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Created by paballo on 2016/03/02.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskDTO implements Comparable<TaskDTO> {

    private final String taskUid;

    private final String title;
    private String description;
    private String location;
    private final String createdByUserName;
    private final boolean createdByUser;

    private final String type;

    private final String parentUid;
    private final String parentName;

    private final String deadline;
    private final String deadlineISO;
    private final Long deadlineMillis;
    private boolean hasResponded;
    private boolean canAction;
    private String reply;

    private final boolean wholeGroupAssigned;
    private final int assignedMemberCount;
    private boolean canEdit;

    @JsonIgnore
    private final Instant instant;
    @JsonIgnore
    private final LocalDateTime deadlineDateTime;

    private TaskDTO(User user, Task task) {
        this.taskUid = task.getUid();
        this.title = task.getName();

        this.parentUid = task.getParent().getUid();
	    this.parentName = task.getParent().getName();

        this.wholeGroupAssigned = task.isAllGroupMembersAssigned();
        this.assignedMemberCount = task.countAssignedMembers();

        this.createdByUser = task.getCreatedByUser().equals(user);
        this.createdByUserName = task.getCreatedByUser().getDisplayName();
        this.type = task.getJpaEntityType().equals(JpaEntityType.LOGBOOK) ? TaskType.TODO.name() : task.getJpaEntityType().name();

        this.instant = task.getDeadlineTime();
        this.deadlineDateTime = task.getDeadlineTimeAtSAST();
        this.deadline = formatAsLocalDateTime(instant);
        this.deadlineISO = this.deadlineDateTime.format(DateTimeFormatter.ISO_DATE_TIME);
        this.deadlineMillis = instant.toEpochMilli();
    }

	/**
     * Definitely not nice to introduce EventLogRepository as constructor dependency, but it currently seems as
     * worthwhile trade-off because it ensures consistency on this object data at multiple places in code.
     */
    public TaskDTO(Event event, User user, EventLogRepository eventLogRepository) {
        this(user, event);

        EventLog eventLog = eventLogRepository.findByEventAndUserAndEventLogType(event, user, EventLogType.RSVP);
//        if (!eventLog.getEventLogType().equals(EventLogType.RSVP)) {
//            throw new IllegalArgumentException("Event log has to be of " + EventLogType.RSVP + ": " + eventLog);
//        }
        this.description =event.getDescription();
        this.hasResponded = eventLog != null;

        this.reply = (eventLog != null && !eventLog.getMessage().equals("Invalid RSVP")) ?
                eventLog.getMessage() :
                String.valueOf(TodoStatus.NO_RESPONSE);
        this.canAction = canActionOnEvent(event, hasResponded);
        this.location = (event.getEventType().equals(EventType.MEETING)) ? ((Meeting) event).getEventLocation() : "";
        this.canEdit = (event.getCreatedByUser().equals(user) && instant.isAfter(Instant.now()));
    }

    public TaskDTO(LogBook logBook, User user) {
        this(user, logBook);

        this.hasResponded = logBook.isCompletedBy(user);
        this.reply = getTodoStatus(logBook);

        this.canAction = canActionOnLogBook(logBook, user);
        this.location = "";
        this.canEdit = logBook.getCreatedByUser().equals(user); // may adjust in future
    }

    public String getTaskUid() {
        return taskUid;
    }
    public String getDescription() {
        return description;
    }

    public String getCreatedByUserName() {
        return createdByUserName;
    }

    public String getParentUid() { return parentUid; }

	public String getParentName() { return parentName; }

    public String getType() {
        return type;
    }

    public String getDeadline() {
        return deadline;
    }

    public LocalDateTime getDeadlineDateTime() { return deadlineDateTime; }

    public String getDeadlineISO() { return deadlineISO; }

    public long getDeadlineMillis() { return deadlineMillis; }

    public String getReply() {
        return reply;
    }

    public boolean isHasResponded() {
        return hasResponded;
    }

    public boolean isCanAction() {
        return canAction;
    }

    public String getTitle() {
        return title;
    }

    public String getLocation() { return location; }

    public boolean isWholeGroupAssigned() { return wholeGroupAssigned; }

    public int getAssignedMemberCount() { return assignedMemberCount; }

    public boolean isCanEdit() { return canEdit; }

	public boolean isCreatedByUser() { return createdByUser; }

    private String getTodoStatus(LogBook logBook) {

        if (logBook.isCompleted()) {
            return String.valueOf(TodoStatus.COMPLETED);
        } else if (logBook.getActionByDate().isBefore(Instant.now())) {
            return String.valueOf(TodoStatus.OVERDUE);
        } else {
            return String.valueOf(TodoStatus.PENDING);
        }

    }

    private boolean canActionOnEvent(Event event, boolean hasResponded) {
        boolean isOpen = event.getEventStartDateTime().isAfter(Instant.now());
        if (event.getEventType().equals(EventType.MEETING) && isOpen) {
            return true;
        } else {
            if (event.getEventType().equals(EventType.VOTE) && (isOpen && !hasResponded)) {
                return true;
            }
        }
        return false;
    }

    private boolean canActionOnLogBook(LogBook logBook, User user) {
        if (!logBook.isCompleted() &&
                (logBook.getAssignedMembers().contains(user)
                || logBook.getAssignedMembers().isEmpty())) {
            return true;
        }
        return false;
    }


    private String formatAsLocalDateTime(Instant instant) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        return DateTimeUtil.convertToUserTimeZone(instant, DateTimeUtil.getSAST()).format(formatter);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TaskDTO taskDTO = (TaskDTO) o;

        if (!taskUid.equals(taskDTO.taskUid)) return false;
        return type.equals(taskDTO.type);

    }

    @Override
    public int hashCode() {
        int result = taskUid.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public int compareTo(TaskDTO o) {
        return ComparisonChain.start()
                .compare(instant,o.instant)
                .compareFalseFirst(hasResponded,o.hasResponded)
                .compareTrueFirst(canAction,o.canAction)
                .result();
    }

    @Override
    public String toString() {
        return "TaskDTO{" +
                "taskUid='" + taskUid + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", location='" + location + '\'' +
                ", createdByUserName='" + createdByUserName + '\'' +
                ", type='" + type + '\'' +
                ", parentUid='" + parentUid + '\'' +
                ", parentName='" + parentName + '\'' +
                ", deadline='" + deadline + '\'' +
                ", deadlineISO='" + deadlineISO + '\'' +
                ", deadlineMillis=" + deadlineMillis +
                ", hasResponded=" + hasResponded +
                ", canAction=" + canAction +
                ", reply='" + reply + '\'' +
                ", wholeGroupAssigned=" + wholeGroupAssigned +
                ", assignedMemberCount=" + assignedMemberCount +
                ", canEdit=" + canEdit +
                ", createdByUser=" + createdByUser +
                ", instant=" + instant +
                ", deadlineDateTime=" + deadlineDateTime +
                '}';
    }
}





