package za.org.grassroot.webapp.model.rest.RequestObjects;

import com.google.common.collect.Sets;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.notification.*;
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
public class NotificationDTO {

    // private static final Logger logger = LoggerFactory.getLogger(NotificationDTO.class);

    private final String uid; // i.e., uid of the notification itself
    private final String entityUid; // i.e., uid of the event/etc that the logbook was attached to

    private final String groupUid;
    private final String title;
    private final String message;
    private final String createdDatetime;
    private final String deadlineDateTime;

    private final String notificationType;
    private final String entityType;
    private final String changeType;

    private final boolean delivered;
    private final boolean read;

    private final static Pattern dialMatcher = Pattern.compile("([\\.,]\\s[Dd].+\\*134\\*1994#.+)");

    // may want to start including join request notifications at some point
    private static final Set<Class<? extends Notification>> validTypesForDTO = Collections.unmodifiableSet(
            Sets.newHashSet(LogBookInfoNotification.class,
                    LogBookReminderNotification.class,
                    EventInfoNotification.class,
                    EventChangedNotification.class,
                    EventCancelledNotification.class,
                    VoteResultsNotification.class));

    public static boolean isNotificationOfTypeForDTO(Notification notification) {
        return validTypesForDTO.contains(notification.getClass());
    }

    public static NotificationDTO convertToDto(Notification notification) {
        if (notification instanceof EventNotification) {
            Event event = ((EventNotification) notification).getEvent();
            return new NotificationDTO(notification, event);
        } else if(notification instanceof LogBookNotification){
            LogBook logBook = ((LogBookNotification) notification).getLogBook();
            return new NotificationDTO(notification,logBook);
        } else {
            throw new IllegalArgumentException("Error! Notification DTO called on unsupported notification type");
        }
    }

    public NotificationDTO(Notification notification, Event event) {
        this.uid = notification.getUid();
        this.entityUid = event.getUid();
        this.title = event.getAncestorGroup().getGroupName();
        this.createdDatetime = convertInstantToStringISO(notification.getCreatedDateTime());
        this.deadlineDateTime = convertInstantToStringISO(event.getDeadlineTime());
        this.groupUid = event.getAncestorGroup().getUid();
        final String originalMessage = (notification.getMessage() != null) ? notification.getMessage() : notification.getEventLog().getMessage();
        this.message = stripDialSuffix(stripTitleFromMessage(title, originalMessage));

        this.delivered = notification.isDelivered();
        this.read = notification.isRead();
        this.notificationType = notification.getNotificationType().toString();
        this.entityType = event.getEventType().toString();
        this.changeType = notification.getEventLog().getEventLogType().toString();
    }

    public NotificationDTO(Notification notification, LogBook logBook){
        this.uid = notification.getUid();
        this.entityUid = logBook.getUid();
        this.title = logBook.getAncestorGroup().getGroupName();
        this.createdDatetime = convertInstantToStringISO(notification.getCreatedDateTime());
        this.deadlineDateTime = convertInstantToStringISO(logBook.getDeadlineTime());
        this.groupUid = logBook.getAncestorGroup().getUid();
        final String originalMessage = (notification.getMessage() != null) ? notification.getMessage() : notification.getLogBookLog().getMessage();
        this.message = stripDialSuffix(stripTitleFromMessage(title, originalMessage));

        this.delivered = notification.isDelivered();
        this.read = notification.isRead();
        this.notificationType = notification.getNotificationType().toString();
        this.entityType = TaskType.TODO.toString();
        this.changeType = notification.getLogBookLog().getType().toString();
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

    public String getNotificationType() {
        return notificationType;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public boolean isRead() {
        return read;
    }

    public String getGroupUid() {
        return groupUid;
    }

    public String getDeadlineDateTime() {
        return deadlineDateTime;
    }

    public String getChangeType() { return changeType; }
}
