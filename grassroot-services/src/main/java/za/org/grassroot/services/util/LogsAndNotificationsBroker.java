package za.org.grassroot.services.util;

public interface LogsAndNotificationsBroker {
	void asyncStoreBundle(LogsAndNotificationsBundle bundle);

	void storeBundle(LogsAndNotificationsBundle bundle);
}
