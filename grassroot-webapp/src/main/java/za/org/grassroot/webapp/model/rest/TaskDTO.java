package za.org.grassroot.webapp.model.rest;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;

/**
 * Created by paballo on 2016/03/02.
 */
public class TaskDTO {

    private Long id;
    String uid;
    private Long appliesToGroup;
    private String subject;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
        this.name = user.getDisplayName();
        this.rsvpRequired = false;



    }

    public Long getAppliesToGroup() {
        return appliesToGroup;
    }

    public void setAppliesToGroup(Long appliesToGroup) {
        this.appliesToGroup = appliesToGroup;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
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

    public void setType(String type) {
        this.type = type;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }


    public boolean isRsvpRequired() {
        return rsvpRequired;
    }

    public void setRsvpRequired(boolean rsvpRequired) {
        this.rsvpRequired = rsvpRequired;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    //Helper functions
    private void populateDTO(Event event){

    }
    private void populateDTO(LogBook logBook){

    }

}
