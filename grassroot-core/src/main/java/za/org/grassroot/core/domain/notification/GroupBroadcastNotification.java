package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationDetailedType;
import za.org.grassroot.core.enums.DeliveryRoute;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("GROUP_BROADCAST")
public class GroupBroadcastNotification extends BroadcastNotification {

    protected GroupBroadcastNotification() {
        // for JPA
    }

    public GroupBroadcastNotification(User destination, String message, DeliveryRoute deliveryChannel,
                                      GroupLog groupLog) {
        super(destination, message, deliveryChannel, groupLog);
        // todo : think about / through priority
    }

    @Override
    public NotificationDetailedType getNotificationDetailedType() {
        return NotificationDetailedType.GROUP_BROADCAST;
    }


}
