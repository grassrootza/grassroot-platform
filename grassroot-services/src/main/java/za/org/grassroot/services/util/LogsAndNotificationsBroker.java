package za.org.grassroot.services.util;

import org.springframework.data.jpa.domain.Specifications;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.Notification;

import java.util.List;

public interface LogsAndNotificationsBroker {
	void asyncStoreBundle(LogsAndNotificationsBundle bundle);

	void storeBundle(LogsAndNotificationsBundle bundle);

	long countNotifications(Specifications<Notification> specifications);

	List<ActionLog> fetchMembershipLogs(Membership membership);

}
