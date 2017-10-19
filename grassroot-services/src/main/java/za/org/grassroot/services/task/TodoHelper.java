package za.org.grassroot.services.task;

import lombok.Builder;
import lombok.Getter;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.task.TodoAssignment;
import za.org.grassroot.core.domain.task.TodoType;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

@Getter @Builder
public class TodoHelper {

    private String userUid;
    private String parentUid;
    private JpaEntityType parentType;

    private TodoType todoType;
    private String description;
    private String responseTag;
    private Instant dueDateTime;

    private Set<TodoAssignment> assignments;

    public void validateMinimumFields() {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(parentUid);
        Objects.requireNonNull(parentType);

        Objects.requireNonNull(todoType);
        Objects.requireNonNull(description);
        Objects.requireNonNull(dueDateTime);

        // todo : add checking logic on assignment set, depending on type
    }

}
