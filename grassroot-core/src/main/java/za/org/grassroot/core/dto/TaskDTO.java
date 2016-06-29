package za.org.grassroot.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ComparisonChain;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.enums.TodoStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Created by paballo on 2016/03/02.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskDTO implements Comparable<TaskDTO> {

    private String taskUid;
    private String title;
    private String description;
    private String location;
    private String createdByUserName;
    private String type;

    private String parentUid;
    private String parentName;

    private String deadline;
    private String deadlineISO;
    private Long deadlineMillis;
    private boolean hasResponded;
    private boolean canAction;
    private String reply;

    private boolean wholeGroupAssigned;
    private int assignedMemberCount;
    private boolean canEdit;
    private boolean createdByUser;

    @JsonIgnore
    private Instant instant;
    @JsonIgnore
    private LocalDateTime deadlineDateTime;

    private TaskDTO(){}

    // todo: try move more stuff into this constructor
    private TaskDTO(AssignedMembersContainer entity) {
        this.taskUid = entity.getUid();
        this.title = entity.getName();
        this.wholeGroupAssigned = entity.isAllGroupMembersAssigned();
        this.assignedMemberCount = entity.countAssignedMembers();
    }

    public TaskDTO(Event event, EventLog eventLog, User user, boolean hasResponded) {
        this(event);
        this.description =event.getDescription();
        this.createdByUserName = event.getCreatedByUser().getDisplayName();
        this.parentUid = event.getParent().getUid();
	    this.parentName = event.getParent().getName();
        this.hasResponded = hasResponded;
        this.type = String.valueOf(event.getEventType());
        this.instant = event.getEventStartDateTime();
        this.deadlineDateTime = event.getEventDateTimeAtSAST();
        this.deadline = formatAsLocalDateTime(instant);
        this.deadlineISO = this.deadlineDateTime.format(DateTimeFormatter.ISO_DATE_TIME);
        this.deadlineMillis = instant.toEpochMilli();
        this.reply= (eventLog !=null && !eventLog.getMessage().equals("Invalid RSVP")) ?
                eventLog.getMessage() : String.valueOf(TodoStatus.NO_RESPONSE);
        this.canAction = canAction(event, user, hasResponded);
        this.location = (event.getEventType().equals(EventType.MEETING)) ? ((Meeting) event).getEventLocation() : "";
        this.canEdit = (event.getCreatedByUser().equals(user) && instant.isAfter(Instant.now()));
	    this.createdByUser = event.getCreatedByUser().equals(user);
    }

    public TaskDTO(LogBook logBook, User user) {
        this(logBook);
        this.parentUid = logBook.getParent().getUid();
	    this.parentName = logBook.getParent().getName();
        this.createdByUserName = logBook.getCreatedByUser().getDisplayName();
        this.hasResponded = logBook.isCompletedBy(user);
        this.reply = getTodoStatus(logBook);
        this.instant = logBook.getActionByDate();
        this.type = String.valueOf(TaskType.TODO);
        this.deadlineDateTime = logBook.getActionByDateAtSAST();
        this.deadline = formatAsLocalDateTime(instant);
        this.deadlineISO = this.deadlineDateTime.format(DateTimeFormatter.ISO_DATE_TIME);
        this.deadlineMillis = instant.toEpochMilli();
        this.canAction = canAction(logBook, user, true);
        this.location = "";
        this.canEdit = logBook.getCreatedByUser().equals(user); // may adjust in future
	    this.createdByUser = logBook.getCreatedByUser().equals(user);
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

    private boolean canAction(Object object, User user, boolean hasResponded) {

        boolean canAction = false;
        if (object instanceof Event) {
            Event event = (Event) object;
            boolean isOpen = event.getEventStartDateTime().isAfter(Instant.now());
            if (event.getEventType().equals(EventType.MEETING) && isOpen) {
                canAction = true;
            } else {
                if (event.getEventType().equals(EventType.VOTE) && (isOpen && !hasResponded)) {
                    canAction = true;
                }
            }
        } else {
            LogBook logBook = (LogBook) object;
            if (!logBook.isCompleted() && (logBook.getAssignedMembers().contains(user)
                    || logBook.getAssignedMembers().isEmpty())) {
                canAction = true;
            }
        }
        return canAction;
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





