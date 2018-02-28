package za.org.grassroot.core.dto.task;

import lombok.Getter;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.enums.TaskType;

@Getter
public class PublicMeetingDTO extends TaskRefDTO{
    public final String parentName;

    public PublicMeetingDTO(Meeting meeting){
        super(meeting.getUid(), TaskType.MEETING,meeting.getName(),meeting.getDeadlineTime());
        this.parentName = meeting.getParent().getName();
    }
}
