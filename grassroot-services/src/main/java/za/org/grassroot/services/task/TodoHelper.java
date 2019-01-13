package za.org.grassroot.services.task;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.task.TodoType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Getter @Builder
public class TodoHelper {

    private String userUid;
    private String parentUid;
    private JpaEntityType parentType;

    private TodoType todoType;
    private String subject;
    private Instant dueDateTime;

    @Setter private String responseTag;

    @Builder.Default private boolean recurring = false;
    private Long recurringPeriodMillis;

    @Setter private Set<String> assignedMemberUids;
    @Setter private Set<String> confirmingMemberUids;

    @Setter private List<String> mediaFileUids;

    private boolean requireImagesForConfirm;

    protected void validateMinimumFields() {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(parentUid);
        Objects.requireNonNull(parentType);

        Objects.requireNonNull(todoType);
        Objects.requireNonNull(subject);
        Objects.requireNonNull(dueDateTime);
    }

    public boolean isInformationTodo() {
        return TodoType.INFORMATION_REQUIRED.equals(todoType);
    }
}
