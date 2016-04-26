package za.org.grassroot.webapp.model.rest;

import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.util.DateTimeUtil;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Created by aakilomar on 12/7/15.
 */
public class LogBookDTO implements Serializable {

    private Long id;
    private Long createdByUserId;
    private JpaEntityType parentType;
    private String parentUid;
    private String message;
    private Long replicatedGroupId;
    private LocalDateTime actionByDate;
    private Long assignToUserId;
    private boolean completed;


    public LogBookDTO() {
    }

    public LogBookDTO(LogBook logBook) {
        this.id = logBook.getId();
        this.createdByUserId = logBook.getCreatedByUser().getId();
        this.parentType = logBook.getJpaEntityType();
        this.parentUid = logBook.getUid();
        this.message = logBook.getMessage();
        this.replicatedGroupId = logBook.getReplicatedGroup().getId();
        this.actionByDate = LocalDateTime.from(logBook.getActionByDate().atZone(DateTimeUtil.getSAST()));
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

    public JpaEntityType getParentType() {
        return parentType;
    }

    public void setParentType(JpaEntityType parentType) {
        this.parentType = parentType;
    }

    public String getParentUid() {
        return parentUid;
    }

    public void setParentUid(String parentUid) {
        this.parentUid = parentUid;
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

    public LocalDateTime getActionByDate() {
        return actionByDate;
    }

    public void setActionByDate(LocalDateTime actionByDate) {
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
                ", parentType=" + parentType +
                ", parentUid=" + parentUid +
                ", message='" + message + '\'' +
                ", replicatedGroupId=" + replicatedGroupId +
                ", actionByDate=" + actionByDate +
                ", assignToUserId=" + assignToUserId +
                ", completed=" + completed +
                '}';
    }
}
