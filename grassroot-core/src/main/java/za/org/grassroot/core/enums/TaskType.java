package za.org.grassroot.core.enums;

import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.Task;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.Vote;

/**
 * Created by paballo on 2016/03/10.
 */
public enum TaskType {
    MEETING,
    VOTE,
    TODO;

    public static TaskType ofClass(Class<? extends Task> clazz) {
        if (clazz == Meeting.class) {
            return MEETING;
        } else if (clazz == Vote.class) {
            return VOTE;
        } else if (clazz == Todo.class) {
            return TODO;
        } else {
            throw new IllegalArgumentException("Unimplemented task type");
        }
    }

    public static Class<? extends Task> toClass(TaskType taskType) {
        switch (taskType) {
            case MEETING: return Meeting.class;
            case VOTE: return Vote.class;
            case TODO: return Todo.class;
            default: throw new IllegalArgumentException("Unimplemented task type");
        }
    }
}
