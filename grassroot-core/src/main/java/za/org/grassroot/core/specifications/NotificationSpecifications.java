package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.account.AccountLog_;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.campaign.CampaignLog_;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.domain.group.GroupLog_;
import za.org.grassroot.core.domain.notification.BroadcastNotification;
import za.org.grassroot.core.domain.notification.BroadcastNotification_;
import za.org.grassroot.core.domain.notification.NotificationStatus;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.enums.*;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Created by luke on 2016/10/06.
 */
public final class NotificationSpecifications {

    public static final List<NotificationStatus> FAILED_STATUS = Arrays.asList(NotificationStatus.DELIVERY_FAILED,
            NotificationStatus.SENDING_FAILED, NotificationStatus.UNDELIVERABLE);

    public static Specification<Notification> toUser(User target) {
        return (root, query, cb) -> cb.equal(root.get(Notification_.target), target);
    }

    public static Specification<Notification> wasDelivered() {
        List<NotificationStatus> deliveredStatuses = Arrays.asList(NotificationStatus.DELIVERED, NotificationStatus.READ);
        return (root, query, cb) -> root.get(Notification_.status).in(deliveredStatuses);
    }

    public static Specification<Notification> createdTimeBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.between(root.get(Notification_.createdDateTime), start, end);
    }

    public static Specification<Notification> belongsToAccount(final Account account) {
        return (root, query, cb) -> {
            Join<Notification, AccountLog> accountLogJoin = root.join(Notification_.accountLog);
            return cb.equal(accountLogJoin.get(AccountLog_.account), account);
        };
    }

    public static Specification<Notification> sharedForCampaign(final Campaign campaign) {
        return (root, query, cb) -> {
            Join<Notification, CampaignLog> campaignLogJoin = root.join(Notification_.campaignLog);
            return cb.and(cb.equal(campaignLogJoin.get(CampaignLog_.campaignLogType), CampaignLogType.CAMPAIGN_SHARED),
                cb.equal(campaignLogJoin.get(CampaignLog_.campaign), campaign));
        };
    }

    public static Specification<Notification> accountLogTypeIs(final AccountLogType accountLogType) {
        return (root, query, cb) -> {
            Join<Notification, AccountLog> accountLogJoin = root.join(Notification_.accountLog);
            return cb.equal(accountLogJoin.get(AccountLog_.accountLogType), accountLogType);
        };
    }

    public static Specification<Notification> userLogTypeIs(final UserLogType userLogType) {
        return (root, query, cb) -> {
            Join<Notification, UserLog> userLogJoin = root.join(Notification_.userLog);
            return cb.equal(userLogJoin.get(UserLog_.userLogType), userLogType);
        };
    }

    public static Specification<Notification> sentOrBetterSince(Instant time) {
        List<NotificationStatus> sentOrBetterStatuses = Arrays.asList(NotificationStatus.SENT, NotificationStatus.DELIVERED, NotificationStatus.READ);
        Specification<Notification> sentOrBetter = (root, query, cb) -> root.get(Notification_.status).in(sentOrBetterStatuses);
        Specification<Notification> statusChangedSince = (root, query, cb) -> cb.greaterThan(root.get(Notification_.lastStatusChange), time);
        return Specification.where(statusChangedSince).and(sentOrBetter);
    }

    public static Specification<Notification> ancestorGroupIs(final Group group) {
        return (root, query, cb) -> {
            Join<Notification, EventLog> eventLogJoin = root.join(Notification_.eventLog, JoinType.LEFT);
            Join<EventLog, Event> eventJoin = eventLogJoin.join(EventLog_.event, JoinType.LEFT);
            Join<Notification, TodoLog> todoLogJoin = root.join(Notification_.todoLog, JoinType.LEFT);
            Join<TodoLog, Todo> todoJoin = todoLogJoin.join(TodoLog_.todo, JoinType.LEFT);
            Join<Notification, AccountLog> accountLogJoin = root.join(Notification_.accountLog, JoinType.LEFT);
            Join<Notification, GroupLog> groupLogJoin = root.join(Notification_.groupLog, JoinType.LEFT);

            return cb.or(cb.or(cb.or(cb.equal(eventJoin.get(Event_.ancestorGroup), group),
                    cb.equal(todoJoin.get(Todo_.ancestorGroup), group)), cb.equal(accountLogJoin.get(AccountLog_.group), group)),
                    cb.equal(groupLogJoin.get(GroupLog_.group), group));
        };
    }

    public static Specification<Notification> ancestorGroupIsTimeLimited(final Group group, Instant fromTime) {
        return (root, query, cb) -> {
            Predicate timeBase = cb.greaterThan(root.get(Notification_.createdDateTime), fromTime);
            Join<Notification, EventLog> eventLogJoin = root.join(Notification_.eventLog, JoinType.LEFT).on(timeBase);
            Join<EventLog, Event> eventJoin = eventLogJoin.join(EventLog_.event, JoinType.LEFT).on(timeBase);
            Join<Notification, TodoLog> todoLogJoin = root.join(Notification_.todoLog, JoinType.LEFT).on(timeBase);
            Join<TodoLog, Todo> todoJoin = todoLogJoin.join(TodoLog_.todo, JoinType.LEFT).on(timeBase);
            Join<Notification, AccountLog> accountLogJoin = root.join(Notification_.accountLog, JoinType.LEFT).on(timeBase);
            Join<Notification, GroupLog> groupLogJoin = root.join(Notification_.groupLog, JoinType.LEFT).on(timeBase);

            return cb.or(cb.or(cb.or(cb.equal(eventJoin.get(Event_.ancestorGroup), group),
                    cb.equal(todoJoin.get(Todo_.ancestorGroup), group)), cb.equal(accountLogJoin.get(AccountLog_.group), group)),
                    cb.equal(groupLogJoin.get(GroupLog_.group), group));
        };
    }

    public static Specification<BroadcastNotification> forEmail() {
        return (root, query, cb) -> root.get(BroadcastNotification_.deliveryChannel).in(DeliveryRoute.EMAIL_ROUTES);
    }

    public static Specification<Notification> isInFailedStatus() {
        return (root, query, cb) -> root.get(Notification_.status).in(FAILED_STATUS);
    }

    public static Specification<Notification> forDeliveryChannel(DeliveryRoute deliveryChannel) {
        return (root, query, cb) -> cb.equal(root.get(Notification_.deliveryChannel), deliveryChannel);
    }

    public static Specification<Notification> forDeliveryChannels(Collection<DeliveryRoute> deliveryChannels) {
        return (root, query, cb) -> root.get(Notification_.deliveryChannel).in(deliveryChannels);
    }

    public static Specification<Notification> unReadUserNotifications(User target, Instant since) {
        return Specification.where(toUser(target))
                .and(createdTimeBetween(since, Instant.now()))
                .and(Specification.not(wasDelivered()));
    }

    public static Specification<Notification> notificationsForSending() {

        Specification<Notification> readyStatus = (root, query, cb) -> cb.equal(root.get("status"), NotificationStatus.READY_FOR_SENDING);

        Instant now = Instant.now();
        Specification<Notification> sendOnlyAfterIsNull = (root, query, cb) -> cb.isNull(root.get("sendOnlyAfter"));
        Specification<Notification> sendOnlyAfterIsInPast = (root, query, cb) -> cb.lessThan(root.get("sendOnlyAfter"), now);
        Specification<Notification> sendOnlyAfterOK = Specification.where(sendOnlyAfterIsNull).or(sendOnlyAfterIsInPast);

        return Specification.where(readyStatus).and(readyStatus).and(sendOnlyAfterOK);
    }

    public static Specification<Notification> messageNotRead() {
        return (root, query, cb) -> cb.notEqual(root.get(Notification_.status), NotificationStatus.READ);
    }

    public static Specification<Notification> unreadAndroidNotifications() {

        Specification<Notification> messageNotUndeliverable = (root, query, cb) -> cb.notEqual(root.get("status"), NotificationStatus.UNDELIVERABLE);
        Specification<Notification> messageNotReadyForSending = (root, query, cb) -> cb.notEqual(root.get("status"), NotificationStatus.READY_FOR_SENDING);
        Specification<Notification> androidChannel = (root, query, cb) -> cb.equal(root.get("deliveryChannel"), DeliveryRoute.ANDROID_APP);
        Specification<Notification> notSentByAat = (root, query, cb) -> cb.notEqual(root.get("sentViaProvider"), MessagingProvider.AAT);

        return Specification
                .where(messageNotRead())
                .and(messageNotUndeliverable)
                .and(messageNotReadyForSending)
                .and(androidChannel); // since sometimes routing header can be GCM but defaults into AAT
    }

    public static Specification<BroadcastNotification> forBroadcast(Broadcast broadcast) {
        return (root, query, cb) -> cb.equal(root.get(BroadcastNotification_.broadcast), broadcast);
    }

    public static Specification<BroadcastNotification> forShortMessage() {
        return (root, query, cb) -> root.get(BroadcastNotification_.deliveryChannel).in(DeliveryRoute.TEXT_ROUTES);
    }

    public static Specification<BroadcastNotification> broadcastFailure() {
        return (root, query, cb) -> root.get(BroadcastNotification_.status).in(FAILED_STATUS);
    }

    public static Specification<BroadcastNotification> broadcastDelivered() {
        List<NotificationStatus> deliveredStatuses = Arrays.asList(NotificationStatus.DELIVERED, NotificationStatus.READ);
        return (root, query, cb) -> root.get(BroadcastNotification_.status).in(deliveredStatuses);
    }

}
