package za.org.grassroot.core.domain.task;

import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;

import javax.persistence.*;
import java.time.Instant;

@Entity @Getter
@Table(name = "action_todo_assigned_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_todo_user_assignment", columnNames = {"action_todo_id", "user_id"}))
public class TodoAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "action_todo_id", nullable = false)
    private Todo todo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "creation_time", nullable = false, updatable = false) // i.e., when the user was assigned to this
    private Instant creationTime;

    @Basic
    @Column(name = "assigned_action")
    @Setter boolean assignedAction;

    @Basic
    @Column(name = "assigned_witness")
    @Setter boolean validator;

    @Basic
    @Column(name = "should_respond")
    @Setter boolean shouldRespond;

    @Basic
    @Column(name = "has_responded")
    @Setter boolean hasResponded;

    @Column(name = "response_date_time")
    @Setter private Instant responseTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_type")
    @Setter private TodoCompletionConfirmType confirmType;

    @Column
    @Setter private String responseText;

    private TodoAssignment() {
        // for JPA
    }

    public TodoAssignment(Todo todo, User user, boolean assignedAction, boolean canWitness, boolean shouldRespond) {
        this.todo = todo;
        this.user = user;
        this.assignedAction = assignedAction;
        this.validator = canWitness;
        this.shouldRespond = shouldRespond;
        this.creationTime = Instant.now();
    }

    protected boolean hasConfirmed() {
        return TodoCompletionConfirmType.COMPLETED.equals(confirmType);
    }

    public boolean canRespond() {
        return shouldRespond && !hasResponded;
    }

    public boolean canConfirm() { return isValidator(); }

}