package za.org.grassroot.core.dto.task;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.util.InstantToMilliSerializer;

import java.time.Instant;

public class TaskTimeChangedDTO {

    private String taskUid;
    private TaskType taskType;

    @JsonSerialize(using = InstantToMilliSerializer.class)
    private Instant lastTaskChange;

    public TaskTimeChangedDTO(String taskUid, Class<? extends Event> type, Instant lastTaskChange) {
        this.taskUid = taskUid;
        this.taskType = TaskType.ofClass(type);
        this.lastTaskChange = lastTaskChange;
    }

    public String getTaskUid() {
        return taskUid;
    }

    public Instant getLastTaskChange() {
        return lastTaskChange;
    }
}
