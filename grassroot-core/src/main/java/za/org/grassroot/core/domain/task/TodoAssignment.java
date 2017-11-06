package za.org.grassroot.core.domain.task;

import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;

@Getter
public class TodoAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    protected String uid;

    @ManyToOne(optional = false)
    @JoinColumn(name = "action_todo_id", nullable = false, foreignKey = @ForeignKey(name = "fk_action_todo_compl_confirm_action_todo"))
    private Todo todo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_action_todo_compl_confirm_member"))
    private User user;

    @Column(name = "creation_time", nullable = false, updatable = false) // i.e., when the user was assigned to this
    private Instant creationTime;

    @Basic
    @Column(name = "assigned")
    boolean assigned;

    @Basic
    @Column(name = "can_confirm")
    boolean canConfirm;

    @Column(name = "confirmation_time")
    @Setter private Instant confirmationTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_type", nullable = false)
    private TodoCompletionConfirmType confirmType;

    @Column
    private String responseText;

    public TodoAssignment(Todo todo, User user, boolean assigned, boolean canConfirm) {
        this.uid = UIDGenerator.generateId();
        this.todo = todo;
        this.user = user;
        this.assigned = assigned;
        this.canConfirm = canConfirm;
        this.creationTime = Instant.now();
    }

}