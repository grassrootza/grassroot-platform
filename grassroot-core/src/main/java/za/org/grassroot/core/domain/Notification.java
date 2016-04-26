package za.org.grassroot.core.domain;


import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.enums.NotificationType;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.util.FormatUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

/**
 * Created by paballo on 2016/04/06.
 */
@Entity
@Table(name ="notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Basic
    @Column(name="creation_time", insertable = true, updatable = false)
    private Instant createdDateTime;

    @ManyToOne
	@JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
	@JoinColumn(name = "event_log_id")
    private EventLog eventLog;

    @ManyToOne
	@JoinColumn(name = "log_book_log_id")
    private LogBookLog logBookLog;

    @ManyToOne
	@JoinColumn(name = "group_log_id", foreignKey = @ForeignKey(name = "fk_notification_group_log"))
    private GroupLog groupLog;

    @Basic
    @Column(name ="read")
    private boolean read =false;

    @Basic
    @Column(name ="delivered")
    private boolean delivered =false;

    @Enumerated
    private UserMessagingPreference userMessagingPreference;

    @Enumerated
    private NotificationType notificationType;

    @Column(name = "message")
    protected String message;

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = Instant.now();
        }
    }

    private Notification(){
        // for JPA
    }

    private Notification(User user, NotificationType notificationType, LogBookLog logBookLog, EventLog eventLog, GroupLog groupLog, String message) {
        this.uid = UIDGenerator.generateId();
        this.read = false;
        this.delivered = false;

        this.user = user;
        this.createdDateTime = Instant.now();
        this.notificationType = notificationType;
        this.userMessagingPreference = user.getMessagingPreference();

        this.logBookLog = logBookLog;
        this.eventLog = eventLog;
        this.groupLog = groupLog;
        this.message = message;
    }

    public Notification(User user, EventLog eventLog, NotificationType notificationType){
        this(user, notificationType, null, eventLog, null, eventLog.getMessage());
    }

    public Notification(User user, GroupLog groupLog, NotificationType notificationType){
        this(user, notificationType, null, null, groupLog, null);
    }

    public Notification(User user, LogBookLog logBookLog, NotificationType notificationType){
        this(user, notificationType, logBookLog, null, null, logBookLog.getMessage());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Instant createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    public EventLog getEventLog() {
        return eventLog;
    }

    public LogBookLog getLogBookLog() {
        return logBookLog;
    }

	public GroupLog getGroupLog() {
		return groupLog;
	}

    public UserMessagingPreference getUserMessagingPreference() {
        return userMessagingPreference;
    }

    public void setUserMessagingPreference(UserMessagingPreference userMessagingPreference) {
        this.userMessagingPreference = userMessagingPreference;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

	/**
     * Locale utilities
     */

    protected Locale getUserLocale() {
   		return getUserLocale(user.getLanguageCode());
   	}

   	protected Locale getUserLocale(String languageCode) {
   		if (languageCode == null || languageCode.trim().equals("")) {
   			return Locale.ENGLISH;
   		} else {
   			return new Locale(languageCode);
   		}

   	}

    protected String constructMessageText(MessageSourceAccessor messageSourceAccessor) {
        return null;
    }

    protected String[] populateEventFields(Event event) {
   		return populateEventFields(event, 0D, 0D, 0D, 0D);
   	}

   	protected String[] populateEventFields(Event event, double yes, double no, double abstain, double noReply) {
   		// todo: switch this to new name (may want a "hasName"/"getName" method defined on UidIdentifiable?
   		String salutation = (((Group) event.getParent()).hasName()) ? ((Group) event.getParent()).getGroupName() : "Grassroot";
   		DateTimeFormatter sdf = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a");
   		String dateString = "no date specified";
   		if (event.getEventStartDateTime() != null) {
   			dateString = sdf.format(event.getEventStartDateTime().atZone(getSAST()));
   		}

   		String location = null;
   		if (event instanceof Meeting) {
   			Meeting meeting = (Meeting) event;
   			location = meeting.getEventLocation();
   		}

   		String[] eventVariables = new String[]{
   				salutation,
   				event.getCreatedByUser().nameToDisplay(),
   				event.getName(),
   				location,
   				dateString,
   				FormatUtil.formatDoubleToString(yes),
   				FormatUtil.formatDoubleToString(no),
   				FormatUtil.formatDoubleToString(abstain),
   				FormatUtil.formatDoubleToString(noReply)
   		};

   		return eventVariables;
   	}

    @Override
    public String toString() {
        return "Notification{" +
                "uid='" + uid + '\'' +
                ", createdDateTime=" + createdDateTime +
                ", user=" + user +
                '}';
    }
}
