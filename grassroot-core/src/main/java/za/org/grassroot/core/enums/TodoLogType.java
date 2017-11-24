package za.org.grassroot.core.enums;

public enum TodoLogType {

	// creation logs
	CREATED,
	CHANGED,

	// modification logs
	ASSIGNED_ADDED,
	VALIDATORS_ADDED,
	ASSIGNED_REMOVED,
	IMAGE_RECORDED,
	IMAGE_REMOVED,
	CANCELLED,
	EXTENDED,

	// notification logs
	REMINDER_SENT,
	RECURRING_SEND,

	RESPONDED
}
