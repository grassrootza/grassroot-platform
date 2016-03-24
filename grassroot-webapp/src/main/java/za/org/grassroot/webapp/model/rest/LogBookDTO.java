package za.org.grassroot.webapp.model.rest;

import za.org.grassroot.core.domain.LogBook;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by aakilomar on 12/7/15.
 */
public class LogBookDTO implements Serializable {

    private Long id;
    private Long createdByUserId;
    private Long groupId;
    private String message;
    private Long replicatedGroupId;
    private Date actionByDate;
    private Long assignToUserId;
    private boolean completed;


    public LogBookDTO() {
    }

    public LogBookDTO(LogBook logBook) {
        this.id = logBook.getId();
        this.createdByUserId = logBook.getCreatedByUser().getId();
        this.groupId = logBook.getGroup().getId();
        this.message = logBook.getMessage();
        this.replicatedGroupId = logBook.getReplicatedGroup().getId();
        this.actionByDate = logBook.getActionByDate();
        this.assignToUserId = null;
        this.completed = logBook.isCompleted();


    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getReplicatedGroupId() {
        return replicatedGroupId;
    }

    public void setReplicatedGroupId(Long replicatedGroupId) {
        this.replicatedGroupId = replicatedGroupId;
    }

    public Date getActionByDate() {
        return actionByDate;
    }

    public void setActionByDate(Date actionByDate) {
        this.actionByDate = actionByDate;
    }

    public Long getAssignToUserId() {
        return assignToUserId;
    }

    public void setAssignToUserId(Long assignToUserId) {
        this.assignToUserId = assignToUserId;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    @Override
    public String toString() {
        return "LogBookDTO{" +
                "id=" + id +
                ", createdByUserId=" + createdByUserId +
                ", groupId=" + groupId +
                ", message='" + message + '\'' +
                ", replicatedGroupId=" + replicatedGroupId +
                ", actionByDate=" + actionByDate +
                ", assignToUserId=" + assignToUserId +
                ", completed=" + completed +
                '}';
    }
}
