package za.org.grassroot.core.domain.task;

import java.util.Arrays;
import java.util.List;

public enum TodoType {

    ACTION_REQUIRED,
    INFORMATION_REQUIRED,
    VOLUNTEERS_NEEDED,
    VALIDATION_REQUIRED;

    public static List<TodoType> typesRequiringResponse() {
        return Arrays.asList(INFORMATION_REQUIRED, VALIDATION_REQUIRED, VOLUNTEERS_NEEDED);
    }

}
