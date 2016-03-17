package za.org.grassroot.webapp.model.rest;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.enums.TodoStatus;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Created by paballo on 2016/03/02.
 */
public abstract class TaskDTO {

    private Long id;
    private Long appliesToGroup;
    private String subject;
    private String name;
    private String type;
    private Timestamp timestamp;
    private boolean hasResponded;
    private String status;
    private String response;
    private String isOverDue;


    public TaskDTO(Event event){
        this.id = event.getId();
        this.subject = event.getName();
        this.name = event.getCreatedByUser().getDisplayName();
        this.hasResponded = event.isRsvpRequired();
        this.type = event.getEventType().toString();
        this.timestamp = event.getEventStartDateTime();
    }
    public TaskDTO(LogBook logBook, User user){
        this.id = logBook.getId();
        this.subject = logBook.getMessage();
        this.name = user.getDisplayName();
        this.hasResponded = false;
    }

    public Long getId() {
        return id;
    }
    public Long getAppliesToGroup() {
        return appliesToGroup;
    }
    public String getSubject() {
        return subject;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getType() {
        return type;
    }
    public Timestamp getTimestamp() {
        return timestamp;
    }
    public String getStatus() {
        return status;
    }
    public String getResponse() {
        return response;
    }

    private String todoStatus(LogBook logBook){

        if(logBook.isCompleted()){
            return String.valueOf(TodoStatus.COMPLETED);
        }
        else if(logBook.getActionByDate().before(Timestamp.from(Instant.now()))){
            return "overdue";
        }else {
            return  "pending";
        }

    }



}
