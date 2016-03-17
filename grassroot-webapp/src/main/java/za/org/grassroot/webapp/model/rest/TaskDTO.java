package za.org.grassroot.webapp.model.rest;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;

/**
 * Created by paballo on 2016/03/02.
 */
public abstract class TaskDTO {

    private Long id;
    private Long appliesToGroup;
    private String subject;
    private String name;
    private String type;
    private String timestamp;
    private boolean rsvpRequired;
    private String status;
    private String response;
    private String isOverDue;


    public TaskDTO(Event event){
        this.id = event.getId();
        this.appliesToGroup = event.getAppliesToGroup().getId();
        this.subject = event.getName();
        this.name = event.getCreatedByUser().getDisplayName();
        this.rsvpRequired = event.isRsvpRequired();
        this.type = event.getEventType().toString();
    }
    public TaskDTO(LogBook logBook, User user){
        this.id = logBook.getId();
        this.appliesToGroup = logBook.getGroupId();
        this.subject = logBook.getMessage();
        this.name = user.getDisplayName();
        this.rsvpRequired = false;
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
    public String getTimestamp() {
        return timestamp;
    }
    public String getStatus() {
        return status;
    }
    public String getResponse() {
        return response;
    }



}
