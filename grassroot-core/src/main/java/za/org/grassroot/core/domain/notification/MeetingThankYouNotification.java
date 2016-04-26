package za.org.grassroot.core.domain.notification;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.NotificationType;

import java.time.format.DateTimeFormatter;

public class MeetingThankYouNotification extends Notification {
	private static final DateTimeFormatter shortDateFormatter = DateTimeFormatter.ofPattern("EEE, d/M");

	public MeetingThankYouNotification(User user, EventLog eventLog) {
		super(user, eventLog, NotificationType.EVENT);
	}

	@Override
	protected String constructMessageText(MessageSourceAccessor messageSourceAccessor) {
		Meeting meeting = (Meeting) getEventLog().getEvent();
		// sms.meeting.thankyou = {0}: Thank you for attending the meeting about {1} on {2}. Dial *134*1994# to create and join groups or call meetings.
		MeetingContainer parent = meeting.getParent();
		String prefix = (parent.getJpaEntityType().equals(JpaEntityType.GROUP) &&
				((Group) parent).hasName()) ? ((Group) parent).getGroupName() : "Grassroot";
		String[] fields = new String[]{prefix, meeting.getName(), meeting.getEventDateTimeAtSAST().format(shortDateFormatter)};
		return messageSourceAccessor.getMessage("sms.meeting.thankyou", fields, getUserLocale());
	}
}
