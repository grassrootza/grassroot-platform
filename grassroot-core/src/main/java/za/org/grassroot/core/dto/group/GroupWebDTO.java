package za.org.grassroot.core.dto.group;

import lombok.Getter;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.dto.task.TaskRefDTO;
import za.org.grassroot.core.enums.TaskType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class GroupWebDTO extends GroupMinimalDTO {

    private List<GroupRefDTO> subGroups = new ArrayList<>();
    private List<TaskRefDTO> comingUpEvents = new ArrayList<>();
    private List<String> topics = new ArrayList<>();

    public GroupWebDTO(Group group, Membership membership, List<GroupRefDTO> subGroups) {
        super(group, membership);
        this.subGroups = subGroups;
        this.comingUpEvents.addAll(
                group.getEvents()
                        .stream()
                        .filter(e -> e.getDeadlineTime().isAfter(Instant.now()))
                        .map(e -> new TaskRefDTO(e.getUid(), TaskType.ofClass(e.getClass()), e.getName(), e.getDeadlineTime()))
                        .collect(Collectors.toList())
        );

        this.comingUpEvents.addAll(
                group.getTodos()
                        .stream()
                        .filter(td -> td.getDeadlineTime().isAfter(Instant.now()))
                        .map(td -> new TaskRefDTO(td.getUid(), TaskType.ofClass(td.getClass()), td.getName(), td.getDeadlineTime()))
                        .collect(Collectors.toList())
        );

        this.topics.addAll(group.getTopics());

    }
}
