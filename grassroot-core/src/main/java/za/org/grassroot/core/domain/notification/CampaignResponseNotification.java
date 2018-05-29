package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("CAMPAIGN_RESPONSE_NOTIFICATION")
public class CampaignResponseNotification extends CampaignNotification {

    private CampaignResponseNotification() {
        // for JPA
    }

    @Override
    public User getSender() {
        return getCampaignLog().getUser();
    }

    public CampaignResponseNotification(User destination, String message, CampaignLog campaignLog) {
        super(destination, message, campaignLog);
        this.priority = AlertPreference.NOTIFY_ONLY_NEW.getPriority(); // since this is important by default
    }

    @Override
    public NotificationDetailedType getNotificationDetailedType() {
        return NotificationDetailedType.CAMPAIGN_RESPONSE;
    }

}
