package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.enums.NotificationDetailedType;
import za.org.grassroot.core.enums.NotificationType;

public abstract class CampaignNotification extends Notification {

    protected CampaignNotification() {
        // for JPA
    }

    public CampaignNotification(User destination, String message, CampaignLog campaignLog) {
        super(destination, message, campaignLog);
    }

    @Override
    public NotificationType getNotificationType() {
        return NotificationType.CAMPAIGN;
    }

    @Override
    public abstract NotificationDetailedType getNotificationDetailedType();

    @Override
    protected void appendToString(StringBuilder sb) {
        sb.append(", campaignLog=").append(getCampaignLog());
    }
}
