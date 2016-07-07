package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Created by aakilomar on 12/3/15.
 */
@Entity
@Table(name = "log_book",
        indexes = {
                @Index(name = "idx_log_book_group_id", columnList = "parent_group_id"),
                @Index(name = "idx_log_book_retries_left", columnList = "number_of_reminders_left_to_send"),
                @Index(name = "idx_log_book_replicated_group_id", columnList = "replicated_group_id")})
public class LogBook extends AbstractLogBookEntity implements Task<LogBookContainer>, VoteContainer, MeetingContainer {
    public static final double COMPLETION_PERCENTAGE_BOUNDARY = 50;

    @Column(name="completed_date")
    private Instant completedDate;

    @Column(name="number_of_reminders_left_to_send")
    private int numberOfRemindersLeftToSend;

    @Column(name="completion_percentage", nullable = false)
    private double completionPercentage;

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

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "logBook", orphanRemoval = true)
    private Set<LogBookCompletionConfirmation> completionConfirmations = new HashSet<>();

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

    public Instant getCompletedDate() {
        return completedDate;
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

    public boolean addCompletionConfirmation(User member, Instant completionTime) {
        Objects.requireNonNull(member);

        if (completionTime == null && this.completedDate == null) {
            throw new IllegalArgumentException("Completion time cannot be null when there is no completed time registered in logbook: " + this);
        }
        // we override current completion time with this latest specified one
        if (completionTime != null) {
            this.completedDate = completionTime;
        }

        Set<User> members = getMembers();
        if (!members.contains(member)) {
            throw new IllegalArgumentException("User " + member + " is not a member of log book: " + this);
        }
        LogBookCompletionConfirmation confirmation = new LogBookCompletionConfirmation(this, member, completionTime);
        boolean confirmationAdded = this.completionConfirmations.add(confirmation);

        this.completionPercentage = calculateCompletionStatus().getPercentage();

        return confirmationAdded;
    }

    public LogBookCompletionStatus calculateCompletionStatus() {
        Set<User> members = getMembers();
        int membersCount = members.size();
        // we count only those confirmations that are from users that
        // are currently members (these can always change)
        long confirmationsCount = completionConfirmations.stream()
                .filter(confirmation -> members.contains(confirmation.getMember()))
                .count();
        return new LogBookCompletionStatus((int) confirmationsCount, membersCount);
    }

    public boolean isCompleted() {
        return calculateCompletionStatus().getPercentage() >= COMPLETION_PERCENTAGE_BOUNDARY;
    }

    public boolean isCompletedBy(User member) {
        Objects.requireNonNull(member);
        return completionConfirmations.stream().anyMatch(confirmation -> confirmation.getMember().equals(member));
    }

    public double getCompletionPercentage() {
        return completionPercentage;
    }

    @Override
    public Instant getDeadlineTime() {
        return actionByDate;
    }

    @Override
    public String toString() {
        return "LogBook{" +
                "id=" + id +
                ", uid=" + uid +
                ", completedDate=" + completedDate +
                ", message='" + message + '\'' +
                ", actionByDate=" + actionByDate +
                ", reminderMinutes=" + reminderMinutes +
                ", numberOfRemindersLeftToSend=" + numberOfRemindersLeftToSend +
                '}';
    }
}
