package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * Created by aakilomar on 12/3/15.
 */
@Entity
@Table(name = "log_book",
        indexes = {@Index(name = "idx_log_book_group_id",  columnList="group_id", unique = false),
        @Index(name = "idx_log_book_completed", columnList="completed",  unique = false),
        @Index(name = "idx_log_book_retries_left", columnList = "number_of_reminders_left_to_send",unique = false),
                @Index(name = "idx_log_book_assigned_to", columnList = "assigned_to_user_id",unique = false),
                @Index(name = "idx_log_book_replicated_group_id", columnList = "replicated_group_id",unique = false)})

public class LogBook extends AbstractLogBookEntity {

    @Column(name = "completed")
    private boolean completed;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name="completed_by_user_id")
    private User completedByUser;

    @Column(name="completed_date")
    private Timestamp completedDate;

    @Column(name="number_of_reminders_left_to_send")
    private int numberOfRemindersLeftToSend;

    @ManyToOne(cascade = CascadeType.ALL)
   	@JoinColumn(name = "replicated_group_id")
   	protected Group replicatedGroup;

    private LogBook() {
        // for JPA
    }

    public LogBook(User createdByUser, Group group, String message, Timestamp actionByDate) {
        this(createdByUser, group, message, actionByDate, 60, null, null, 3);
    }

    public LogBook(User createdByUser, Group group, String message, Timestamp actionByDate, int reminderMinutes,
                   User assignedToUser, Group replicatedGroup, int numberOfRemindersLeftToSend) {
        super(createdByUser, group, message, actionByDate, reminderMinutes, assignedToUser);
        this.replicatedGroup = replicatedGroup;
        this.numberOfRemindersLeftToSend = numberOfRemindersLeftToSend;
    }

    public static LogBook makeEmpty() {
        LogBook logBook = new LogBook();
        logBook.uid = UIDGenerator.generateId();
        return logBook;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public User getCompletedByUser() {
        return completedByUser;
    }

    public void setCompletedByUser(User completedByUser) {
        this.completedByUser = completedByUser;
    }

    public Timestamp getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(Timestamp completedDate) {
        this.completedDate = completedDate;
    }

    public int getNumberOfRemindersLeftToSend() {
        return numberOfRemindersLeftToSend;
    }

    public void setNumberOfRemindersLeftToSend(int numberOfRemindersLeftToSend) {
        this.numberOfRemindersLeftToSend = numberOfRemindersLeftToSend;
    }

    public Group getReplicatedGroup() {
        return replicatedGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        LogBook logBook = (LogBook) o;

        if (uid != null ? !uid.equals(logBook.uid) : logBook.uid != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return uid != null ? uid.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "LogBook{" +
                "id=" + id +
                ", uid=" + uid +
                ", createdDateTime=" + createdDateTime +
                ", completed=" + completed +
                ", completedDate=" + completedDate +
                ", message='" + message + '\'' +
                ", actionByDate=" + actionByDate +
                ", reminderMinutes=" + reminderMinutes +
                ", numberOfRemindersLeftToSend=" + numberOfRemindersLeftToSend +
                '}';
    }
}
