package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.LogBook;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;

/**
 * Created by aakilomar on 12/12/15.
 */
public class LogBookDTO implements Serializable {


    private Long id;
    private Long groupId;
    private boolean completed;
    private Long completedByUserId;
    private Timestamp completedDate;
    private String message;
    private Long assignedToUserId;
    private Long replicatedGroupId;
    private Timestamp actionByDate;
    private int reminderMinutes;
    private int numberOfRemindersLeftToSend;


    public LogBookDTO() {
    }

    public LogBookDTO(LogBook logBook) {
        this.id =  logBook.getId();
        this.actionByDate = logBook.getActionByDate();
        this.completed = logBook.isCompleted();
        this.completedByUserId = logBook.getCompletedByUser() == null ? null : logBook.getCompletedByUser().getId();
        this.message = logBook.getMessage();
        this.assignedToUserId = logBook.getAssignedToUser() == null ? null : logBook.getAssignedToUser().getId();
        this.replicatedGroupId = logBook.getReplicatedGroup() == null ? null : logBook.getReplicatedGroup().getId();
        this.completedDate = logBook.getCompletedDate();
        this.reminderMinutes = logBook.getReminderMinutes();
        this.numberOfRemindersLeftToSend = logBook.getNumberOfRemindersLeftToSend();
        this.groupId = logBook.getGroup().getId();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Long getCompletedByUserId() {
        return completedByUserId;
    }

    public void setCompletedByUserId(Long completedByUserId) {
        this.completedByUserId = completedByUserId;
    }

    public Date getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(Timestamp completedDate) {
        this.completedDate = completedDate;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getAssignedToUserId() {
        return assignedToUserId;
    }

    public void setAssignedToUserId(Long assignedToUserId) {
        this.assignedToUserId = assignedToUserId;
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

    public void setActionByDate(Timestamp actionByDate) {
        this.actionByDate = actionByDate;
    }

    public int getReminderMinutes() {
        return reminderMinutes;
    }

    public void setReminderMinutes(int reminderMinutes) {
        this.reminderMinutes = reminderMinutes;
    }

    public int getNumberOfRemindersLeftToSend() {
        return numberOfRemindersLeftToSend;
    }

    public void setNumberOfRemindersLeftToSend(int numberOfRemindersLeftToSend) {
        this.numberOfRemindersLeftToSend = numberOfRemindersLeftToSend;
    }

    @Override
    public String toString() {
        return "LogBookDTO{" +
                "id=" + id +
                ", groupId=" + groupId +
                ", completed=" + completed +
                ", completedByUserId=" + completedByUserId +
                ", completedDate=" + completedDate +
                ", message='" + message + '\'' +
                ", assignedToUserId=" + assignedToUserId +
                ", replicatedGroupId=" + replicatedGroupId +
                ", actionByDate=" + actionByDate +
                ", reminderMinutes=" + reminderMinutes +
                ", numberOfRemindersLeftToSend=" + numberOfRemindersLeftToSend +
                '}';
    }
}
