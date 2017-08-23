package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.livewire.LiveWireLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * Created by luke on 2017/05/16.
 */
@Entity
@DiscriminatorValue("LIVEWIRE_MADE_CONTACT")
public class LiveWireMadeContactNotification extends LiveWireNotification {
    @Override
    public NotificationDetailedType getNotificationDetailedType() {
        return NotificationDetailedType.LIVEWIRE_MADE_CONTACT;
    }

    private LiveWireMadeContactNotification() {
        // for JPA
    }

    public LiveWireMadeContactNotification(User target, String message, LiveWireLog liveWireLog) {
        super(target, message, liveWireLog);
        this.priority = AlertPreference.NOTIFY_ONLY_NEW.getPriority();
    }
}
