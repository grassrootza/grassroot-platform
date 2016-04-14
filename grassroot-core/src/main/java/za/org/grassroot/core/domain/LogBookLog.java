package za.org.grassroot.core.domain;

import javax.persistence.*;
import java.util.Date;

/**
 * Created by aakilomar on 12/3/15.
 */
@Entity
@Table(name = "log_book_log",
        indexes = {@Index(name = "idx_log_book_log_logbook_id",  columnList="logbook_id", unique = false)})

public class LogBookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Basic
    @Column(name="created_date_time", insertable = true, updatable = false)
    private Date createdDateTime;
    @Basic
    @Column(name="logbook_id")
    private Long logBookId;

    @ManyToOne
    private LogBook logBook;

    @Basic
    @Column
    private String message;
    @Basic
    @Column(name="user_id")
    private Long userId;
    @Basic
    @Column(name="group_id")
    private Long groupId;
    @Basic
    /*
    Phonenumber or email address, or any other future identifier
     */
    @Column(name="message_to")
    private String messageTo;

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = new Date();
        }
    }

    // Constructors


    public LogBookLog() {
    }

    public LogBookLog(Long logBookId, String message, Long userId, Long groupId, String messageTo) {
        this.logBookId = logBookId;
        this.message = message;
        this.userId = userId;
        this.groupId = groupId;
        this.messageTo = messageTo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Date createdDateTime) {
        this.createdDateTime = createdDateTime;
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

    public Long getLogBookId() {
        return logBookId;
    }

    public void setLogBookId(Long logBookId) {
        this.logBookId = logBookId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMessageTo() {
        return messageTo;
    }

    public void setMessageTo(String messageTo) {
        this.messageTo = messageTo;
    }

    @Override
    public String toString() {
        return "LogBookLog{" +
                "id=" + id +
                ", createdDateTime=" + createdDateTime +
                ", logBookId=" + logBookId +
                ", message='" + message + '\'' +
                ", userId=" + userId +
                ", groupId=" + groupId +
                ", messageTo='" + messageTo + '\'' +
                '}';
    }

    public LogBook getLogBook() {
        return logBook;
    }

    public void setLogBook(LogBook logBook) {
        this.logBook = logBook;
    }
}
