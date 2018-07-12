package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("GROUP_BROADCAST")
public class GroupBroadcastNotification extends BroadcastNotification {

    protected GroupBroadcastNotification() {
        // for JPA
    }

    public GroupBroadcastNotification(User destination, String message, Broadcast broadcast,
                                      DeliveryRoute deliveryChannel, GroupLog groupLog) {
        super(destination, message, deliveryChannel, groupLog, broadcast);
        // todo : think about / through priority
    }

    @Override
    public NotificationDetailedType getNotificationDetailedType() {
        return NotificationDetailedType.GROUP_BROADCAST;
    }


}
