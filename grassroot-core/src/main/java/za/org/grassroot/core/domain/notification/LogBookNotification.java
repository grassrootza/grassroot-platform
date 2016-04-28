package za.org.grassroot.core.domain.notification;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.NotificationType;

import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import java.text.SimpleDateFormat;

@MappedSuperclass
public abstract class LogBookNotification extends Notification {
	@ManyToOne
	@JoinColumn(name = "log_book_id")
	private LogBook logBook;

	@Override
	public NotificationType getNotificationType() {
		return NotificationType.LOGBOOK;
	}

	protected LogBookNotification() {
		// for JPA
	}

	protected LogBookNotification(User target, String message, LogBookLog logBookLog) {
		this(target, message, logBookLog, logBookLog.getLogBook());
	}

	protected LogBookNotification(User target, String message, ActionLog actionLog, LogBook logBook) {
		super(target, message, actionLog);
		this.logBook = logBook;
	}

	public LogBook getLogBook() {
		return logBook;
	}

	protected String[] populateLogBookFields(User target, LogBook logBook) {
		Group group = logBook.resolveGroup();
		String salutation = (group.hasName()) ? group.getGroupName() : "GrassRoot";
		SimpleDateFormat sdf = new SimpleDateFormat("EEE d MMM, h:mm a");
		String dateString = "no date specified";
		if (logBook.getActionByDate() != null) {
			dateString = sdf.format(logBook.getActionByDate());
		}
		String[] variables = new String[]{
				salutation,
				logBook.getMessage(),
				dateString,
				target.getDisplayName()
		};
		return variables;
	}

	protected String constructMessageText(MessageSourceAccessor messageSourceAccessor) {
		return message;
	}
}
