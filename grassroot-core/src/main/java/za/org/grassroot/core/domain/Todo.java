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
@Table(name = "action_todo",
        indexes = {
                @Index(name = "idx_action_todo_group_id", columnList = "parent_group_id"),
                @Index(name = "idx_action_todo_retries_left", columnList = "number_of_reminders_left_to_send"),
                @Index(name = "idx_action_todo_replicated_group_id", columnList = "replicated_group_id")})
public class Todo extends AbstractTodoEntity implements Task<TodoContainer>, VoteContainer, MeetingContainer {

    public static final double COMPLETION_PERCENTAGE_BOUNDARY = 50;
    public static final int DEFAULT_NUMBER_REMINDERS = 2;

    @Column(name = "cancelled")
    protected boolean cancelled;

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
    @JoinTable(name = "action_todo_assigned_members",
            joinColumns = @JoinColumn(name = "log_book_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "user_id", nullable = false)
    )
    private Set<User> assignedMembers = new HashSet<>();

    @ManyToOne
   	@JoinColumn(name = "ancestor_group_id", nullable = false)
   	private Group ancestorGroup;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "todo", orphanRemoval = true)
    private Set<TodoCompletionConfirmation> completionConfirmations = new HashSet<>();

    private Todo() {
        // for JPA
    }

    public Todo(User createdByUser, TodoContainer parent, String message, Instant actionByDate) {
        this(createdByUser, parent, message, actionByDate, 60, null, DEFAULT_NUMBER_REMINDERS, true);
    }

    public Todo(User createdByUser, TodoContainer parent, String message, Instant actionByDate, int reminderMinutes,
                Group replicatedGroup, int numberOfRemindersLeftToSend, boolean reminderActive) {
        super(createdByUser, parent, message, actionByDate, reminderMinutes, reminderActive);
        this.ancestorGroup = parent.getThisOrAncestorGroup();
        this.replicatedGroup = replicatedGroup;
        this.numberOfRemindersLeftToSend = numberOfRemindersLeftToSend;
        this.cancelled = false;
    }

    public static Todo makeEmpty() {
        Todo todo = new Todo();
        todo.uid = UIDGenerator.generateId();
        return todo;
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
        return JpaEntityType.TODO;
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
        TodoCompletionConfirmation confirmation = new TodoCompletionConfirmation(this, member, completionTime);
        boolean confirmationAdded = this.completionConfirmations.add(confirmation);

        this.completionPercentage = calculateCompletionStatus().getPercentage();

        return confirmationAdded;
    }

    public TodoCompletionStatus calculateCompletionStatus() {
        Set<User> members = getMembers();
        int membersCount = members.size();
        // we count only those confirmations that are from users that
        // are currently members (these can always change)
        long confirmationsCount = completionConfirmations.stream()
                .filter(confirmation -> members.contains(confirmation.getMember()))
                .count();
        return new TodoCompletionStatus((int) confirmationsCount, membersCount);
    }

    public boolean isCancelled() { return cancelled; }

    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

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
        return "Todo{" +
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
