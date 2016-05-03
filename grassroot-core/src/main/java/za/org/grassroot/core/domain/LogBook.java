package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by aakilomar on 12/3/15.
 */
@Entity
@Table(name = "log_book",
        indexes = {
                @Index(name = "idx_log_book_group_id", columnList = "parent_group_id"),
                @Index(name = "idx_log_book_completed", columnList = "completed"),
                @Index(name = "idx_log_book_retries_left", columnList = "number_of_reminders_left_to_send"),
                @Index(name = "idx_log_book_replicated_group_id", columnList = "replicated_group_id")})
public class LogBook extends AbstractLogBookEntity implements AssignedMembersContainer, VoteContainer, MeetingContainer, GroupDescendant {

    @Column(name = "completed")
    private boolean completed;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name="completed_by_user_id")
    private User completedByUser;

    @Column(name="completed_date")
    private Instant completedDate;

    @Column(name="number_of_reminders_left_to_send")
    private int numberOfRemindersLeftToSend;

    @ManyToOne(cascade = CascadeType.ALL)
   	@JoinColumn(name = "replicated_group_id")
   	private Group replicatedGroup;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "log_book_assigned_members",
            joinColumns = @JoinColumn(name = "log_book_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "user_id", nullable = false)
    )
    private Set<User> assignedMembers = new HashSet<>();

    @ManyToOne
   	@JoinColumn(name = "ancestor_group_id", nullable = false)
   	private Group ancestorGroup;

    private LogBook() {
        // for JPA
    }

    public LogBook(User createdByUser, LogBookContainer parent, String message, Instant actionByDate) {
        this(createdByUser, parent, message, actionByDate, 60, null, 3);
    }

    public LogBook(User createdByUser, LogBookContainer parent, String message, Instant actionByDate, int reminderMinutes,
                   Group replicatedGroup, int numberOfRemindersLeftToSend) {
        super(createdByUser, parent, message, actionByDate, reminderMinutes);
        this.ancestorGroup = parent.getThisOrAncestorGroup();
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

    public Instant getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(Instant completedDate) {
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
    public Group getAncestorGroup() {
        return ancestorGroup;
    }

    @Override
    public String getName() { return message; }

    @Override
    public JpaEntityType getJpaEntityType() {
        return JpaEntityType.LOGBOOK;
    }

    @Override
    public Set<User> fetchAssignedMembersCollection() {
        return assignedMembers;
    }

    @Override
    public void putAssignedMembersCollection(Set<User> assignedMembersCollection) {
        this.assignedMembers = assignedMembersCollection;
    }

    @Override
    public String toString() {
        return "LogBook{" +
                "id=" + id +
                ", uid=" + uid +
                ", completed=" + completed +
                ", completedDate=" + completedDate +
                ", message='" + message + '\'' +
                ", actionByDate=" + actionByDate +
                ", reminderMinutes=" + reminderMinutes +
                ", numberOfRemindersLeftToSend=" + numberOfRemindersLeftToSend +
                '}';
    }
}
