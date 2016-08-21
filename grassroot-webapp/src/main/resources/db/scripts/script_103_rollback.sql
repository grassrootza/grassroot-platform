ALTER TABLE verification_token_code DROP COLUMN token_type;
ALTER TABLE verification_token_code ALTER COLUMN username DROP NOT NULL;
ALTER TABLE verification_token_code ALTERpublic Meeting(String name, Instant startDateTime, User user, MeetingContainer parent, String eventLocation, boolean includeSubGroups,
				   boolean rsvpRequired, boolean relayable, EventReminderType reminderType, int customReminderMinutes, String description) {
		super(startDateTime, user, parent, name, includeSubGroups, rsvpRequired, relayable, reminderType, customReminderMinutes, description);
		this.eventLocation = Objects.requireNonNull(eventLocation);
		setScheduledReminderActive(true);
		setParent(parent);
	} COLUMN creation_date DROP NOT NULL;