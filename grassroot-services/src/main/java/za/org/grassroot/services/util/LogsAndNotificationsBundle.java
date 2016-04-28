package za.org.grassroot.services.util;

import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Notification;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class LogsAndNotificationsBundle {
	private final Set<ActionLog> logs;
	private final Set<Notification> notifications;

	public LogsAndNotificationsBundle(Set<ActionLog> logs, Set<Notification> notifications) {
		this.logs = Objects.requireNonNull(logs);
		this.notifications = Objects.requireNonNull(notifications);
	}

	public LogsAndNotificationsBundle() {
		this(new HashSet<>(), new HashSet<>());
	}

	public void addLog(ActionLog log) {
		logs.add(Objects.requireNonNull(log));
	}

	public void addNotification(Notification notification) {
		notifications.add(Objects.requireNonNull(notification));
	}

	public void addLogs(Set<ActionLog> logs) {
		this.logs.addAll(Objects.requireNonNull(logs));
	}

	public void addNotifications(Set<Notification> notifications) {
		this.notifications.addAll(Objects.requireNonNull(notifications));
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
