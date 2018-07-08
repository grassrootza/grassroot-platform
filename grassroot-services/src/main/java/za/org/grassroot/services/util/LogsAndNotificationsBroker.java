package za.org.grassroot.services.util;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.Specifications;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.enums.CampaignLogType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface LogsAndNotificationsBroker {
	void asyncStoreBundle(LogsAndNotificationsBundle bundle);

	void storeBundle(LogsAndNotificationsBundle bundle);

	long countNotifications(Specification<Notification> specifications);

	<T extends Notification> long countNotifications(Specifications<T> specs, Class<T> notificationType);

	List<ActionLog> fetchMembershipLogs(Membership membership);

	long countCampaignLogs(Specification<CampaignLog> specs);

	List<PublicActivityLog> fetchMostRecentPublicLogs(Integer numberLogs);

	void updateCache(Collection<ActionLog> actionLogs);

	void abortNotificationSend(Specification specifications);

	Page<Notification> lastNotificationsSentToUser(User user, Integer numberToRetrieve, Instant sinceTime);

	void removeCampaignLog(User user, Campaign campaign, CampaignLogType logType);

}
