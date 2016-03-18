package za.org.grassroot.webapp.model.rest;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.webapp.enums.TaskType;
import za.org.grassroot.webapp.enums.TodoStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Created by paballo on 2016/03/02.
 */
public class TaskDTO implements Comparator<TaskDTO> ,Comparable<TaskDTO>{

    private Long id;
    private String description;
    private String name;
    private String type;
    private Timestamp deadline;
    private boolean hasResponded;
    private boolean canAction;
    private String reply;



    public TaskDTO(Event event, EventLog eventLog, User user, boolean hasResponded) {
        this.id = event.getId();
        this.description = event.getName();
        this.name = event.getCreatedByUser().getDisplayName();
        this.hasResponded = hasResponded;
        this.type = String.valueOf(event.getEventType());
        this.deadline = event.getEventStartDateTime();
        this.reply=(eventLog !=null)?eventLog.getMessage():String.valueOf(TodoStatus.NO_RESPONSE);
        this.canAction = canAction(event, user, hasResponded);
    }

    public TaskDTO(LogBook logBook, User user, User creatingUser) {
        this.id = logBook.getId();
        this.description = logBook.getMessage();
        this.name = creatingUser.getDisplayName();
        this.hasResponded = false;
        this.reply = getLogStatus(logBook);
        this.deadline = logBook.getActionByDate();
        this.type = String.valueOf(TaskType.LOG);
        this.canAction = canAction(logBook, user, true);
    }

    public Long getId() {
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

    public Timestamp getDeadline() {
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


    private String getLogStatus(LogBook logBook) {

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
            if (event.getEventType().equals(EventType.Meeting) && isOpen) {
                canAction = true;
            } else {
                if (event.getEventType().equals(EventType.Vote) && isOpen && !hasResponded) {
                    canAction = true;
                }
            }
        } else {
            LogBook logBook = (LogBook) object;
            if (logBook.getAssignedToUserId().equals(user.getId()) || logBook.getCreatedByUserId().equals(user.getId())
                    || logBook.getAssignedToUserId().equals(null)) {
                canAction = true;
            }
        }
        return canAction;
    }

    @Override
    public int compare(TaskDTO o1, TaskDTO o2) {
        return o1.getDeadline().compareTo(o2.getDeadline());
    }

    @Override
    public int compareTo(TaskDTO o) {
        return (int)(this.deadline.getTime()-o.deadline.getTime());
    }
}





