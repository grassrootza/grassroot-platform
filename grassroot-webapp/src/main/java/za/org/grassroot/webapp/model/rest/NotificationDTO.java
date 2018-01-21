package za.org.grassroot.webapp.model.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.notification.EventNotification;
import za.org.grassroot.core.domain.notification.TodoNotification;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.Task;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.NotificationDetailedType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by paballo on 2016/04/13.
 */
@Slf4j
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationDTO {

    private final String uid; // i.e., uid of the notification itself
    private final String notificationType;
    private final boolean delivered;
    private final boolean read;
    private final boolean viewedAndroid;

    private final String groupUid;
    private final String title;
    private final String imageUrl;
    private final String defaultImage;

    private String entityUid; // i.e., uid of the event/etc that the logbook was attached to
    private String message;
    private String createdDatetime;
    private String deadlineDateTime;

    private String entityType;
    private String changeType;

    // should start including join request notifications & rsvp totals at some point
    private static final Set<NotificationDetailedType> notificationsForAndroidList =
            Collections.unmodifiableSet(Sets.newHashSet(
                    NotificationDetailedType.TODO_INFO,
                    NotificationDetailedType.TODO_REMINDER,
                    NotificationDetailedType.EVENT_INFO,
                    NotificationDetailedType.EVENT_CHANGED,
                    NotificationDetailedType.EVENT_CANCELLED,
                    NotificationDetailedType.EVENT_REMINDER,
                    NotificationDetailedType.VOTE_RESULTS,
                    NotificationDetailedType.MEETING_RSVP_TOTALS));

    private final static Pattern dialMatcher = Pattern.compile("([\\.,]\\s[Dd].+\\*134\\*1994#.+)");

    public static boolean isNotificationOfTypeForDTO(Notification notification) {
        return notificationsForAndroidList.contains(notification.getNotificationDetailedType());
    }

    public static NotificationDTO convertToDto(Notification notification) {
        if (notification instanceof EventNotification) {
            Event event = ((EventNotification) notification).getEvent();
            return new NotificationDTO(notification, event);
        } else if(notification instanceof TodoNotification){
            Todo todo = ((TodoNotification) notification).getTodo();
            return new NotificationDTO(notification,todo);
        } else {
            throw new IllegalArgumentException("Error! Notification DTO called on unsupported notification type");
        }
    }

    private NotificationDTO(Notification notification, Task task) {
        this.uid = notification.getUid();
        this.createdDatetime = convertInstantToStringISO(notification.getCreatedDateTime());
        this.delivered = notification.isDelivered();
        this.read = notification.isRead();
        this.viewedAndroid = notification.isViewedOnAndroid();
        this.notificationType = notification.getNotificationDetailedType().toString();

        this.title = task.getAncestorGroup().getGroupName();
        this.groupUid = task.getAncestorGroup().getUid();
        this.imageUrl = task.getAncestorGroup().getImageUrl();
        this.defaultImage = task.getAncestorGroup().getDefaultImage().toString();
    }

    private NotificationDTO(Notification notification, Event event) {
        this(notification, (Task) event);

        this.entityUid = event.getUid();
        this.deadlineDateTime = convertInstantToStringISO(event.getDeadlineTime());

        this.message = stripDialSuffix(stripTitleFromMessage(title, notification.getMessage()));

        this.entityType = event.getEventType().toString();
        this.changeType = notification.getEventLog() == null ? EventLogType.CREATED.toString() :
                notification.getEventLog().getEventLogType().toString();
    }

    private NotificationDTO(Notification notification, Todo todo){
        this(notification, (Task) todo);

        this.entityUid = todo.getUid();
        this.deadlineDateTime = convertInstantToStringISO(todo.getDeadlineTime());

        final String originalMessage = (notification.getMessage() != null) ? notification.getMessage() : notification.getTodoLog().getMessage();
        this.message = stripDialSuffix(stripTitleFromMessage(title, originalMessage));

        this.entityType = TaskType.TODO.toString();
        this.changeType = notification.getTodoLog().getType().toString();
    }

    private String convertInstantToStringISO(Instant instant) {
        return DateTimeUtil.convertToUserTimeZone(instant, DateTimeUtil.getSAST()).format(DateTimeFormatter.ISO_DATE_TIME);
    }

    private String stripTitleFromMessage(final String title, final String message) {
        if (!message.contains(title)) {
            return message;
        } else {
            final Pattern groupNamePatter = Pattern.compile("^" + title + "\\s?:\\s+?");
            final Matcher m = groupNamePatter.matcher(message);
            if (m.find()) {
                return message.substring(m.end());
            } else {
                return message;
            }
        }
    }

    private String stripDialSuffix(final String message) {
        final Matcher m = dialMatcher.matcher(message);
        if (m.find()) {
            return message.substring(0, m.start());
        } else {
            return message;
        }
    }

    public String getEntityType() {
        return entityType;
    }

    public String getUid() {
        return uid;
    }

    public String getEntityUid() {
        return entityUid;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getCreatedDatetime() {
        return createdDatetime;
    }

    public void setCreatedDatetime(String createdDatetime) {
        this.createdDatetime = createdDatetime;
    }

    public void setDeadlineDateTime(String deadlineDateTime) {
        this.deadlineDateTime = deadlineDateTime;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public boolean isRead() {
        return read;
    }

    public boolean isViewedAndroid() { return viewedAndroid; }

    public String getGroupUid() {
        return groupUid;
    }

    public String getDeadlineDateTime() {
        return deadlineDateTime;
    }

    public String getChangeType() { return changeType; }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getDefaultImage() {
        return defaultImage;
    }
}
