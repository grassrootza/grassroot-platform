package za.org.grassroot.webapp.model.rest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ComparisonChain;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.webapp.enums.TodoStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Created by paballo on 2016/03/02.
 */
public class TaskDTO implements Comparable<TaskDTO>{

    private String id;
    private String title;
    private String description;
    private String name;
    private String type;
    private String deadline;
    private boolean hasResponded;
    private boolean canAction;
    private String reply;
    @JsonIgnore
    private Instant instant;
    @JsonIgnore
    private LocalDateTime deadlineDateTime;
    @JsonIgnore
    private String parentName; // only use in Web at present, hence Json ignore, may change in future
    @JsonIgnore
    private String location; // only use in Web at present, hence Json ignore, may change in future

    public TaskDTO(){}

    public TaskDTO(Event event, EventLog eventLog, User user, boolean hasResponded) {
        this.id = event.getUid();
        this.title = event.getName();
        this.description =event.getDescription();
        this.name = event.getCreatedByUser().getDisplayName();
        this.parentName = event.getParent().getName();
        this.hasResponded = hasResponded;
        this.type = String.valueOf(event.getEventType());
        this.instant = event.getEventStartDateTime();
        this.deadlineDateTime = event.getEventDateTimeAtSAST();
        this.deadline = formatAsLocalDateTime(instant);
        this.reply= (eventLog !=null && !eventLog.getMessage().equals("Invalid RSVP")) ?
                eventLog.getMessage() : String.valueOf(TodoStatus.NO_RESPONSE);
        this.canAction = canAction(event, user, hasResponded);
        this.location = (event.getEventType().equals(EventType.MEETING)) ? ((Meeting) event).getEventLocation() : "";
    }

    public TaskDTO(LogBook logBook, User user, User creatingUser) {
        this.id = logBook.getUid();
        this.title = logBook.getMessage();
        this.parentName = logBook.getParent().getName();
        this.name = creatingUser.getDisplayName();
        this.hasResponded = (logBook.isCompleted())?true:false;
        this.reply = getTodoStatus(logBook);
        this.instant = logBook.getActionByDate();
        this.type = String.valueOf(TaskType.TODO);
        this.deadlineDateTime = logBook.getActionByDateAtSAST();
        this.deadline = formatAsLocalDateTime(instant);
        this.canAction = canAction(logBook, user, true);
        this.location = "";
    }

    public String getId() {
        return id;
    }
    public String getDescription() {
        return description;
    }

    public String getName() {
        return name;
    }

    public String getParentName() { return parentName; }

    public String getType() {
        return type;
    }

    public String getDeadline() {
        return deadline;
    }

    public LocalDateTime getDeadlineDateTime() { return deadlineDateTime; }

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
        if (hasResponded != taskDTO.hasResponded) return false;
        if (canAction != taskDTO.canAction) return false;
        if (!id.equals(taskDTO.id)) return false;
        if (!title.equals(taskDTO.title)) return false;
        if (description != null ? !description.equals(taskDTO.description) : taskDTO.description != null) return false;
        if (!name.equals(taskDTO.name)) return false;
        if (!type.equals(taskDTO.type)) return false;
        if (!deadline.equals(taskDTO.deadline)) return false;
        if (!reply.equals(taskDTO.reply)) return false;
        return instant.equals(taskDTO.instant);

    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + title.hashCode();
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + deadline.hashCode();
        result = 31 * result + (hasResponded ? 1 : 0);
        result = 31 * result + (canAction ? 1 : 0);
        result = 31 * result + reply.hashCode();
        result = 31 * result + instant.hashCode();
        return result;
    }


    @Override
    public int compareTo(TaskDTO o) {
        return ComparisonChain.start().compareFalseFirst(hasResponded,o.hasResponded)
                .compareTrueFirst(canAction,o.canAction)
                .compare(deadline,o.deadline)
                .result();
    }



}





