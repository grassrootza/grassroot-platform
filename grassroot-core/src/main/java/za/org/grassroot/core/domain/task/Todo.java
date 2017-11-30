package za.org.grassroot.core.domain.task;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by aakilomar on 12/3/15.
 */
@Entity @Getter @Slf4j
@Table(name = "action_todo",
        indexes = {
                @Index(name = "idx_action_todo_group_id", columnList = "parent_group_id"),
                @Index(name = "idx_action_todo_ancestor_group_id", columnList = "ancestor_group_id"),
                @Index(name = "index_action_todo_type ", columnList = "todo_type")})
public class Todo extends AbstractTodoEntity implements Task<TodoContainer>, VoteContainer, MeetingContainer {

    private static final int DEFAULT_REMINDER_MINUTES = 60; // send it at deadline

    @Column(name = "next_notification_time")
    @Setter private Instant nextNotificationTime;

    @Column(name = "cancelled")
    protected boolean cancelled;

    @Column(name="completed")
    @Setter private boolean completed;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "todo", orphanRemoval = true)
    @Setter private Set<TodoAssignment> assignments = new HashSet<>();

    @ManyToOne
   	@JoinColumn(name = "ancestor_group_id", nullable = false)
   	@Getter private Group ancestorGroup;

    @Basic
    @Column(name = "response_regex")
    @Getter @Setter private String responseRegex;

    @Basic
    @Column(name = "require_images")
    @Getter @Setter private boolean requireImages;

    @Basic
    @Column(name = "allow_simple")
    @Getter @Setter private boolean allowSimpleConfirmation;

    @Basic
    @Column(name = "recurring")
    @Getter @Setter private boolean recurring = false;

    @Basic
    @Column(name = "recurring_interval")
    @Getter @Setter private Long recurInterval;

    private Todo() {
        // for JPA
    }

    public Todo(User createdByUser, TodoContainer parent, TodoType todoType, String description, Instant dueByDate) {
        super(createdByUser, parent, todoType, description, dueByDate, parent.getTodoReminderMinutes(), true);

        this.ancestorGroup = parent.getThisOrAncestorGroup();
        this.ancestorGroup.addDescendantTodo(this);
        this.cancelled = false;
        this.completed = false;

        calculateScheduledReminderTime();
    }

    public static Todo makeEmpty() {
        Todo todo = new Todo();
        todo.uid = UIDGenerator.generateId();
        return todo;
    }

    public LocalDateTime getReminderTimeAtSAST() { return nextNotificationTime.atZone(DateTimeUtil.getSAST()).toLocalDateTime(); }

    public void calculateScheduledReminderTime() {
        this.nextNotificationTime= reminderActive
                ? DateTimeUtil.restrictToDaytime(actionByDate.minus(reminderMinutes, ChronoUnit.MINUTES), actionByDate,
                DateTimeUtil.getSAST()) : null;

        // if reminder time is already in the past (e.g., set to 1 week but deadline in 5 days), try set it to tomorrow, else set it to deadline
        if (reminderActive && this.nextNotificationTime.isBefore(Instant.now())) {
            if (Instant.now().plus(1, ChronoUnit.DAYS).isBefore(actionByDate)) {
                this.nextNotificationTime= DateTimeUtil.restrictToDaytime(Instant.now().plus(1, ChronoUnit.DAYS),
                        actionByDate, DateTimeUtil.getSAST());
            } else {
                this.nextNotificationTime = actionByDate;
            }
        }
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.TODO;
    }

    @Override
    public String getName() { return message; }

    public String getCreatorAlias() {
        return ancestorGroup.getMembership(this.createdByUser).getDisplayName();
    }

    @Override
    public boolean hasName() { return !StringUtils.isEmpty(message); }

    @Override
    public JpaEntityType getJpaEntityType() {
        return JpaEntityType.TODO;
    }

    @Override
    public Set<User> fetchAssignedMembersCollection() {
        return assignments.stream().map(TodoAssignment::getUser).collect(Collectors.toSet());
    }

    @Override
    public void putAssignedMembersCollection(Set<User> assignedMembersCollection) {
        this.assignments = assignedMembersCollection.stream()
                .map(u -> new TodoAssignment(this, u, true, false, false)).collect(Collectors.toSet());
    }

    @Override
    public Set<User> assignMembers(Set<String> memberUids) {
        Objects.requireNonNull(memberUids);

        log.info("adding some members: {}", memberUids);
        Group group = getAncestorGroup();
        Map<String, User> membersByUid = group.getMembers().stream().collect(Collectors.toMap(User::getUid, member -> member));
        Set<User> existingMembers = assignments.stream().map(TodoAssignment::getUser).collect(Collectors.toSet());

        Set<User> membersToAssign = memberUids.stream()
                .flatMap(uid -> {
                    User member = membersByUid.get(uid);
                    if (member != null && !existingMembers.contains(member)) {
                        return Stream.of(member);
                    } else {
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toSet());

        assignments.addAll(membersToAssign.stream().map(u -> new TodoAssignment(this, u, true, false, false))
                .collect(Collectors.toSet()));

        log.info("added member, assignments now of size: {}", assignments.size());
        return membersToAssign;
    }

    // todo : make sure equals and hash code are working properly here (via tests - e.g., create a list of assignments,
    // then remove one by a call to list.remove, and see if it is actually removed)
    public void addAssignments(Set<TodoAssignment> todoAssignments) {
        if (this.assignments == null) {
            this.assignments = new HashSet<>();
        }

        this.assignments.addAll(todoAssignments);
    }

    public Set<User> getAssignedUsers() {
        return assignments.stream()
                .filter(TodoAssignment::isAssignedAction)
                .map(TodoAssignment::getUser)
                .collect(Collectors.toSet());
    }

    public Set<User> getConfirmingUsers() {
        return assignments.stream()
                .filter(TodoAssignment::isValidator)
                .map(TodoAssignment::getUser)
                .collect(Collectors.toSet());
    }

    public boolean addCompletionConfirmation(User member,
                                             TodoCompletionConfirmType confirmType,
                                             Instant completionTime) {
        Objects.requireNonNull(member);

        Optional<TodoAssignment> findAssignment = assignments.stream()
                .filter(TodoAssignment::isValidator)
                .filter(a -> a.getUser().equals(member))
                .findFirst();

        if (!findAssignment.isPresent()) {
            throw new IllegalArgumentException("Trying to add completion confirmation for non-assigned user");
        }

        TodoAssignment assignment = findAssignment.get();
        assignment.setResponseTime(completionTime);
        assignment.setConfirmType(confirmType);

        return true;
    }

    public boolean canUserRespond(User member) {
        Objects.requireNonNull(member);
        return assignments.stream()
                .filter(TodoAssignment::canRespond)
                .anyMatch(a -> a.getUser().equals(member));
    }

    public boolean hasUserResponded(User member) {
        Objects.requireNonNull(member);
        return assignments.stream()
                .filter(TodoAssignment::isHasResponded)
                .anyMatch(a -> a.getUser().equals(member));
    }

    // note : only returns yes if response type is "completed"
    public boolean isCompletionConfirmedByMember(User member) {
        Objects.requireNonNull(member);
        return assignments.stream()
                .filter(TodoAssignment::hasConfirmed)
                .anyMatch(a -> a.getUser().equals(member));
    }

    public boolean isCancelled() { return cancelled; }

    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public Instant getDeadlineTime() {
        return actionByDate;
    }

    @Override
    public String toString() {
        return "Todo{" +
                "id=" + id +
                ", uid=" + uid +
                ", completed=" + completed +
                ", message='" + message + '\'' +
                ", actionByDate=" + actionByDate +
                ", reminderMinutes=" + reminderMinutes +
                '}';
    }
}
