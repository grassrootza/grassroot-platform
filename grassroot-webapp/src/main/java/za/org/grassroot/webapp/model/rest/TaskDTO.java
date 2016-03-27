package za.org.grassroot.webapp.model.rest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ComparisonChain;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.webapp.enums.TaskType;
import za.org.grassroot.webapp.enums.TodoStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

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
    private Timestamp timestamp;



    public TaskDTO(){}

    public TaskDTO(Event event, EventLog eventLog, User user, boolean hasResponded) {
        this.id = event.getUid();
        this.title = event.getName();
        this.description =event.getDescription();
        this.name = event.getCreatedByUser().getDisplayName();
        this.hasResponded = hasResponded;
        this.type = String.valueOf(event.getEventType());
        this.timestamp = event.getEventStartDateTime();
        this.deadline =getLocalDateTime(timestamp);
        this.reply=(eventLog !=null)?eventLog.getMessage():String.valueOf(TodoStatus.NO_RESPONSE);
        this.canAction = canAction(event, user, hasResponded);
    }

    public TaskDTO(LogBook logBook, User user, User creatingUser) {
        this.id = logBook.getUid();
        this.title = logBook.getMessage();
        this.name = creatingUser.getDisplayName();
        this.hasResponded = (logBook.isCompleted())?true:false;
        this.reply = getTodoStatus(logBook);
        this.timestamp = logBook.getActionByDate();
        this.type = String.valueOf(TaskType.TODO);
        this.deadline = getLocalDateTime(timestamp);
        this.canAction = canAction(logBook, user, true);
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


    public String getType() {
        return type;
    }

    public String getDeadline() {
        return deadline;
    }

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


    private String getTodoStatus(LogBook logBook) {

        if (logBook.isCompleted()) {
            return String.valueOf(TodoStatus.COMPLETED);
        } else if (logBook.getActionByDate().before(Timestamp.from(Instant.now()))) {
            return String.valueOf(TodoStatus.OVERDUE);
        } else {
            return String.valueOf(TodoStatus.PENDING);
        }

    }

    private boolean canAction(Object object, User user, boolean hasResponded) {

        boolean canAction = false;
        if (object instanceof Event) {
            Event event = (Event) object;
            boolean isOpen = event.getEventStartDateTime().after(Timestamp.from(Instant.now()));
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


    private String getLocalDateTime(Timestamp timestamp) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        return timestamp.toLocalDateTime().atZone(ZoneId.of("Africa/Johannesburg")).format(formatter);
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
        return timestamp.equals(taskDTO.timestamp);

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
        result = 31 * result + timestamp.hashCode();
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





