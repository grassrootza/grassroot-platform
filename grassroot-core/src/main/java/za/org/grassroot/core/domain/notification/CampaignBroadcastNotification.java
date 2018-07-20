package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("CAMPAIGN_BROADCAST")
public class CampaignBroadcastNotification extends BroadcastNotification {

    protected CampaignBroadcastNotification() {
        // for JPA
    }

    public CampaignBroadcastNotification(User destination, String message, Broadcast broadcast, DeliveryRoute deliveryChannel,
                                         CampaignLog campaignLog) {
        super(destination, message, deliveryChannel, campaignLog, broadcast);
        // todo : think about / through priority
    }

    @Override
    public NotificationDetailedType getNotificationDetailedType() {
        return NotificationDetailedType.CAMPAIGN_BROADCAST;
    }


}
