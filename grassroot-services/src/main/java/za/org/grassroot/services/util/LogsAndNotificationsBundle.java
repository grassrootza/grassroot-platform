package za.org.grassroot.services.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Notification;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Bundle of ActionLogs and Notifications.
 * Performs filtering of registering Notifications based on their priority and notification priority of target user.
 */
public class LogsAndNotificationsBundle {

	// private static final Logger logger = LoggerFactory.getLogger(LogsAndNotificationsBundle.class);

	private final Set<ActionLog> logs;
	private final Set<Notification> notifications;

	public LogsAndNotificationsBundle(Set<ActionLog> logs, Set<Notification> notifications) {
		this.logs = Objects.requireNonNull(logs);
		this.notifications = Objects.requireNonNull(notifications);
	}

	public LogsAndNotificationsBundle() {
		this(new HashSet<>(), new HashSet<>());
	}

	public void addNotification(Notification notification) {
		Objects.requireNonNull(notification);
		addNotifications(Collections.singleton(notification));
	}

	public void addNotifications(Set<Notification> notifications) {
		Objects.requireNonNull(notifications);
		notifications.stream()
				.filter(Notification::isPrioritySatisfiedByTarget)
				.forEach(this.notifications::add);
	}

	public void addLog(ActionLog log) {
		logs.add(Objects.requireNonNull(log));
	}

	public void addLogs(Set<ActionLog> logs) {
		this.logs.addAll(Objects.requireNonNull(logs));
	}

	public void addBundle(LogsAndNotificationsBundle bundle) {
		Objects.requireNonNull(bundle);
		addLogs(bundle.getLogs());
		addNotifications(bundle.getNotifications());
	}

	public Set<ActionLog> getLogs() {
		return logs;
	}

	public Set<Notification> getNotifications() {
		return notifications;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("LogsAndNotificationsBundle{");
		sb.append("logs=").append(logs);
		sb.append(", notifications=").append(notifications);
		sb.append('}');
		return sb.toString();
	}
}
