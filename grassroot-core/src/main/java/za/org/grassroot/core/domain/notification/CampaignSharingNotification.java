package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("CAMPAIGN_SHARING_NOTIFICATION")
public class CampaignSharingNotification extends CampaignNotification {

    private CampaignSharingNotification() {
        // for JPA
    }

    public CampaignSharingNotification(User destination, String message, CampaignLog campaignLog) {
        super(destination, message, campaignLog);
        this.priority = AlertPreference.NOTIFY_ONLY_NEW.getPriority(); // since this is important by default
    }

    @Override
    public NotificationDetailedType getNotificationDetailedType() {
        return NotificationDetailedType.CAMPAIGN_SHARE;
    }


}
