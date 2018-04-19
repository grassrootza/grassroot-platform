package za.org.grassroot.services.util;

import org.springframework.data.jpa.domain.Specifications;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.Notification;

import java.util.Collection;
import java.util.List;

public interface LogsAndNotificationsBroker {
	void asyncStoreBundle(LogsAndNotificationsBundle bundle);

	void storeBundle(LogsAndNotificationsBundle bundle);

	long countNotifications(Specifications<Notification> specifications);

	<T extends Notification> long countNotifications(Specifications<T> specs, Class<T> notificationType);

	List<ActionLog> fetchMembershipLogs(Membership membership);

	List<PublicActivityLog> fetchMostRecentPublicLogs(Integer numberLogs);

	void updateCache(Collection<ActionLog> actionLogs);

}
