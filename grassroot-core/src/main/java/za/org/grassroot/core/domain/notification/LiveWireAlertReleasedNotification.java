package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.LiveWireLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * Created by luke on 2017/05/16.
 */
@Entity
@DiscriminatorValue("LIVEWIRE_ALERT_RELEASED")
public class LiveWireAlertReleasedNotification extends LiveWireNotification {
    @Override
    public NotificationDetailedType getNotificationDetailedType() {
        return NotificationDetailedType.LIVEWIRE_TO_REVIEW;
    }

    private LiveWireAlertReleasedNotification() {
        // for JPA
    }

    public LiveWireAlertReleasedNotification(User destination, String message, LiveWireLog log) {
        super(destination, message, log);
        this.priority = AlertPreference.NOTIFY_ONLY_NEW.getPriority();
    }
}
