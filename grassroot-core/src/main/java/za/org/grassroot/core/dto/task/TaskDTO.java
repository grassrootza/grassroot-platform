package za.org.grassroot.core.dto.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ComparisonChain;
import io.swagger.annotations.ApiModel;
import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.TodoStatus;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.StringArrayUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.data.jpa.domain.Specifications.where;
import static za.org.grassroot.core.specifications.EventLogSpecifications.*;

/**
 * Created by paballo on 2016/03/02.
 */
@Getter
@ApiModel(value = "Task", description = "Generic DTO for meetings, votes, and actions")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskDTO implements Comparable<TaskDTO> {

    private final String taskUid;

    private final String title;
    private String description;
    private String location;
    private final String createdByUserName;
    private final boolean createdByUser;

    private final String type;

    private final String parentUid;
    private final String parentName;

    private final String deadline;
    private final String deadlineISO;
    private final String deadlineAtSAST;
    private final Long deadlineMillis;
    private boolean hasResponded;
    private boolean canAction;
    private String reply;

    private final boolean wholeGroupAssigned;
    private final int assignedMemberCount;
    private boolean canEdit;

    private List<String> tags;
    @Setter private Map<String, Long> voteCount;

    private TodoType todoType;

    @JsonIgnore
    private final Instant instant;
    @JsonIgnore
    private final LocalDateTime deadlineDateTime;

    private TaskDTO(User user, Task task) {
        this.taskUid = task.getUid();
        this.title = task.getName();

        this.parentUid = task.getParent().getUid();
	    this.parentName = task.getParent().getName();

        this.wholeGroupAssigned = task.isAllGroupMembersAssigned();
        this.assignedMemberCount = task.countAssignedMembers();

        this.createdByUser = task.getCreatedByUser().equals(user);
        this.createdByUserName = task.getCreatedByUser().getDisplayName();
        this.type = task.getJpaEntityType().equals(JpaEntityType.TODO) ? TaskType.TODO.name() : task.getJpaEntityType().name();

        this.instant = task.getDeadlineTime();
        this.deadlineDateTime = task.getDeadlineTimeAtSAST();
        this.deadline = formatAsLocalDateTime(instant);
        this.deadlineISO = this.deadlineDateTime.format(DateTimeFormatter.ISO_DATE_TIME);
        this.deadlineAtSAST = this.deadlineDateTime.format(DateTimeFormatter.ISO_DATE_TIME);
        this.deadlineMillis = instant.toEpochMilli();
    }

	/**
     * Definitely not nice to introduce EventLogRepository as constructor dependency, but it currently seems as
     * worthwhile trade-off because it ensures consistency on this object data at multiple places in code.
     */
    public TaskDTO(Event event, User user, EventLogRepository eventLogRepository) {
        this(event, user, eventLogRepository.findOne(where(forEvent(event))
                .and(forUser(user)).and(isResponseToAnEvent())));
    }

    public TaskDTO(Event event, User user, EventLog eventLog) {
        this(user, event);
        if (eventLog != null && !eventLog.isResponseToEvent()) {
            throw new IllegalArgumentException("Event log has to be a response to an event: " + eventLog);
        }

        this.description =event.getDescription();
        this.hasResponded = eventLog != null;

        this.reply = (eventLog != null && eventLog.hasValidResponse()) ?
                eventLog.isVoteResponse() ? eventLog.getTag() : eventLog.getResponse().toString() :
                String.valueOf(TodoStatus.NO_RESPONSE);
        this.canAction = canActionOnEvent(event, user);
        this.location = event.getEventType().equals(EventType.MEETING) ? ((Meeting) event).getEventLocation() : "";
        this.canEdit = event.getCreatedByUser().equals(user) && instant.isAfter(Instant.now());

        this.tags = StringArrayUtil.arrayToList(event.getTags());
    }

    public TaskDTO(Todo todo, User user) {
        this(user, todo);
        this.hasResponded = todo.hasUserResponded(user);
        this.reply = hasResponded ? String.valueOf(TodoStatus.COMPLETED) : getTodoStatus(todo, user);
        this.canAction = todo.canUserRespond(user);
        this.location = "";
        this.canEdit = todo.getCreatedByUser().equals(user); // may adjust in future
        this.tags = new ArrayList<>();
        this.todoType = todo.getType();
    }

    private String getTodoStatus(Todo todo, User user) {
        if (todo.isCompletionConfirmedByMember(user)) {
            return String.valueOf(TodoStatus.COMPLETED);
        } else if (todo.getActionByDate().isBefore(Instant.now())) {
            return String.valueOf(TodoStatus.OVERDUE);
        } else {
            return String.valueOf(TodoStatus.PENDING);
        }
    }

    // for the moment, users can change their vote after casting it
    private boolean canActionOnEvent(Event event, User user) {
        boolean isOpen = event.getEventStartDateTime().isAfter(Instant.now());
        // slight redundancy here but may introduce alternate logics here in future, hence
        if (event.getEventType().equals(EventType.MEETING) && isOpen) {
            return true;
        } else if (event.getEventType().equals(EventType.VOTE) && (isOpen)) {
            Membership membership = event.getAncestorGroup().getMembership(user);
            return membership != null && membership.getJoinTime().isBefore(event.getCreatedDateTime());
        }
        return false;
    }

    private String formatAsLocalDateTime(Instant instant) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        return DateTimeUtil.convertToUserTimeZone(instant, DateTimeUtil.getSAST()).format(formatter);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TaskDTO taskDTO = (TaskDTO) o;

        if (!taskUid.equals(taskDTO.taskUid)) return false;
        return type.equals(taskDTO.type);

    }

    @Override
    public int hashCode() {
        int result = taskUid.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public int compareTo(TaskDTO o) {
        return ComparisonChain.start()
                .compare(instant,o.instant)
                .compareFalseFirst(hasResponded,o.hasResponded)
                .compareTrueFirst(canAction,o.canAction)
                .result();
    }

    @Override
    public String toString() {
        return "TaskDTO{" +
                "taskUid='" + taskUid + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", location='" + location + '\'' +
                ", createdByUserName='" + createdByUserName + '\'' +
                ", type='" + type + '\'' +
                ", parentUid='" + parentUid + '\'' +
                ", parentName='" + parentName + '\'' +
                ", deadline='" + deadline + '\'' +
                ", deadlineISO='" + deadlineISO + '\'' +
                ", deadlineMillis=" + deadlineMillis +
                ", hasResponded=" + hasResponded +
                ", canAction=" + canAction +
                ", reply='" + reply + '\'' +
                ", wholeGroupAssigned=" + wholeGroupAssigned +
                ", assignedMemberCount=" + assignedMemberCount +
                ", canEdit=" + canEdit +
                ", createdByUser=" + createdByUser +
                ", instant=" + instant +
                ", deadlineDateTime=" + deadlineDateTime +
                '}';
    }
}





