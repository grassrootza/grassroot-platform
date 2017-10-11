package za.org.grassroot.core.domain.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Created by aakilomar on 12/3/15.
 */
@Entity
@Table(name = "action_todo",
        indexes = {
                @Index(name = "idx_action_todo_group_id", columnList = "parent_group_id"),
                @Index(name = "idx_action_todo_retries_left", columnList = "number_of_reminders_left_to_send"),
                @Index(name = "idx_action_todo_ancestor_group_id", columnList = "ancestor_group_id")})
public class Todo extends AbstractTodoEntity implements Task<TodoContainer>, VoteContainer, MeetingContainer {

    // private static final Logger logger = LoggerFactory.getLogger();

    @Transient
    @Value("{grassroot.todos.number.reminders:1")
    private int DEFAULT_NUMBER_REMINDERS;

    @Column(name = "cancelled")
    protected boolean cancelled;

    @Column(name="completed_date")
    private Instant completedDate;

    @Column(name="number_of_reminders_left_to_send")
    private int numberOfRemindersLeftToSend;

    @Column(name="completion_percentage", nullable = false)
    private double completionPercentage;

    @ManyToOne
    @JoinColumn(name = "source_todo")
    private Todo sourceTodo;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "action_todo_assigned_members",
            joinColumns = @JoinColumn(name = "action_todo_id", nullable = false),
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
        this(createdByUser, parent, message, actionByDate, 60, null, null, true);
    }

    public Todo(User createdByUser, TodoContainer parent, String message, Instant actionByDate, int reminderMinutes,
                Todo sourceTodo, Integer numberOfRemindersLeftToSend, boolean reminderActive) {
        super(createdByUser, parent, message, actionByDate, reminderMinutes, reminderActive);

        this.ancestorGroup = parent.getThisOrAncestorGroup();
        this.ancestorGroup.addDescendantTodo(this);

        this.sourceTodo = sourceTodo;
        this.numberOfRemindersLeftToSend = numberOfRemindersLeftToSend == null ? DEFAULT_NUMBER_REMINDERS : numberOfRemindersLeftToSend;
        this.cancelled = false;
    }

    public static Todo makeEmpty() {
        Todo todo = new Todo();
        todo.uid = UIDGenerator.generateId();
        return todo;
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

    @Override
    public Group getAncestorGroup() {
        return ancestorGroup;
    }

    @Override
    public String getName() { return message; }

    @Override
    public boolean hasName() { return !StringUtils.isEmpty(message); }

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

    public boolean addCompletionConfirmation(User member, TodoCompletionConfirmType confirmType, Instant completionTime,
                                             double threshold) {
        Objects.requireNonNull(member);

        if (completionTime == null && this.completedDate == null) {
            throw new IllegalArgumentException("Completion time cannot be null when there is no completed time registered in todo: " + this);
        }

        // we override current completion time with this latest specified one
        if (completionTime != null) {
            this.completedDate = completionTime;
        }

        Set<User> members = getMembers();
        if (!members.contains(member)) {
            throw new IllegalArgumentException("User " + member + " is not assigned to or in the group of this todo: " + this);
        }

        Optional<TodoCompletionConfirmation> confirmByMember = this.completionConfirmations.stream()
                .filter(tc -> tc.getMember().equals(member)).findFirst();
        TodoCompletionConfirmation confirmation;

        if (confirmByMember.isPresent()) { // i.e., user is switching response
            confirmation = confirmByMember.get();
            confirmation.setConfirmType(confirmType);
        } else {
            confirmation = new TodoCompletionConfirmation(this, member, confirmType, completionTime);
        }

        this.completionConfirmations.add(confirmation);
        boolean wasBelowThreshold = this.completionPercentage < threshold;
        this.completionPercentage = calculateCompletionStatus().getPercentage();

        return wasBelowThreshold && (this.completionPercentage > threshold);
    }

    private TodoCompletionStatus calculateCompletionStatus() {
        Set<User> members = getMembers();
        int membersCount = members.size();
        // we count only those confirmations that mark as complete and are from users that are currently members (these can always change)
        long confirmationsCount = completionConfirmations.stream()
                .filter(confirmation -> TodoCompletionConfirmType.COMPLETED.equals(confirmation.getConfirmType())
                        && members.contains(confirmation.getMember()))
                .count();
        return new TodoCompletionStatus((int) confirmationsCount, membersCount);
    }

    public boolean isCompleted(double threshold) {
        return calculateCompletionStatus().getPercentage() >= threshold;
    }

    public int countCompletions() {
        return completionConfirmations.size();
    }

    public boolean hasUserResponded(User member) {
        Objects.requireNonNull(member);
        return completionConfirmations.stream().anyMatch(c -> c.getMember().equals(member));
    }

    // note : only returns yes if response type is "completed"
    public boolean isCompletionConfirmedByMember(User member) {
        Objects.requireNonNull(member);
        return completionConfirmations.stream().anyMatch(confirmation -> confirmation.getConfirmType().equals(TodoCompletionConfirmType.COMPLETED)
                && confirmation.getMember().equals(member));
    }

    public double getCompletionPercentage() { return completionPercentage; }

    public boolean isCancelled() { return cancelled; }

    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public TaskType getTaskType() {
        return TaskType.TODO;
    }

    @Override
    public Instant getDeadlineTime() {
        return actionByDate;
    }

    public Todo getSourceTodo() { return sourceTodo; }

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
