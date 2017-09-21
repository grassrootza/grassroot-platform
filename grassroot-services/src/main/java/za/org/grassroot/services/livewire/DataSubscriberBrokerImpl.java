package za.org.grassroot.services.livewire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.livewire.DataSubscriber;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.enums.DataSubscriberType;
import za.org.grassroot.core.repository.DataSubscriberRepository;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.PermissionBroker;

import java.util.*;

/**
 * Created by luke on 2017/05/05.
 */
@Service
public class DataSubscriberBrokerImpl implements DataSubscriberBroker {

    private static final Logger logger = LoggerFactory.getLogger(DataSubscriberBrokerImpl.class);

    private static final String liveWireRoleName = "ROLE_LIVEWIRE_USER";

    private final DataSubscriberRepository dataSubscriberRepository;
    private final UserRepository userRepository;
    private final PermissionBroker permissionBroker;
    private final RoleRepository roleRepository;

    @Autowired
    public DataSubscriberBrokerImpl(DataSubscriberRepository dataSubscriberRepository, UserRepository userRepository, PermissionBroker permissionBroker, RoleRepository roleRepository) {
        this.dataSubscriberRepository = dataSubscriberRepository;
        this.userRepository = userRepository;
        this.permissionBroker = permissionBroker;
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DataSubscriber> listSubscribers(final boolean activeOnly, final Sort sort) {
        return activeOnly ? dataSubscriberRepository.findByActiveTrue(sort) :
                dataSubscriberRepository.findAll(sort);
    }

    @Override
    public List<DataSubscriber> listPublicSubscribers() {
        return dataSubscriberRepository.findByActiveTrueAndSubscriberType(DataSubscriberType.PUBLIC,
                new Sort(Sort.Direction.ASC, "displayName"));
    }

    @Override
    @Transactional(readOnly = true)
    public DataSubscriber validateSubscriberAdmin(String viewingUserUid, String subscriberUid) {
        Objects.requireNonNull(viewingUserUid);
        Objects.requireNonNull(subscriberUid);

        User user = userRepository.findOneByUid(viewingUserUid);
        DataSubscriber subscriber = dataSubscriberRepository.findOneByUid(subscriberUid);

        validateAdminUser(user, subscriber);

        return subscriber;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean doesUserHaveCustomLiveWireList(String userUid) {
        return dataSubscriberRepository.findSubscriberHoldingUser(userUid)
                .stream()
                .filter(DataSubscriber::isActive)
                .anyMatch(DataSubscriber::hasPushEmails);
    }

    // note: could also do this with Specifications but for moment that would be premature optimization
    @Override
    @Transactional
    public DataSubscriber fetchLiveWireListForSubscriber(String userUid) {
        return dataSubscriberRepository.findSubscriberHoldingUser(userUid)
                .stream()
                .filter(DataSubscriber::isActive)
                .filter(DataSubscriber::hasPushEmails)
                .sorted(Comparator.comparing(DataSubscriber::getCreationTime, Comparator.reverseOrder()))
                .findFirst().orElse(null);
    }

    @Override
    @Transactional
    public void create(final String sysAdminUid, final String displayName, final String primaryEmail, boolean addPrimaryEmailToPush, List<String> additionalPushEmails, boolean active) {
        Objects.requireNonNull(sysAdminUid);
        Objects.requireNonNull(displayName);

        logger.info("Creating LiveWire subscriber, with params: addPrimaryEmail: {}, additionalMails: {}, active: {}",
                addPrimaryEmailToPush, additionalPushEmails, active);

        User sysAdmin = userRepository.findOneByUid(sysAdminUid);
        permissionBroker.validateSystemRole(sysAdmin, "ROLE_SYSTEM_ADMIN");

        DataSubscriber subscriber = new DataSubscriber(sysAdmin, sysAdmin, displayName, primaryEmail, active, DataSubscriberType.PRIVATE);

        if (addPrimaryEmailToPush) {
            subscriber.addEmailsForPushNotification(Collections.singletonList(primaryEmail));
        }

        if (additionalPushEmails != null && !additionalPushEmails.isEmpty()) {
            subscriber.addEmailsForPushNotification(additionalPushEmails);
        }

        dataSubscriberRepository.save(subscriber);
    }

    @Override
    @Transactional
    public void updateActiveStatus(final String sysAdminUid, final String subscriberUid, final boolean active) {
        Objects.requireNonNull(sysAdminUid);
        Objects.requireNonNull(subscriberUid);

        User sysAdmin = userRepository.findOneByUid(sysAdminUid);
        permissionBroker.validateSystemRole(sysAdmin, "ROLE_SYSTEM_ADMIN");

        DataSubscriber subscriber = dataSubscriberRepository.findOneByUid(subscriberUid);
        subscriber.setActive(active);
    }

    @Override
    @Transactional
    public void addPushEmails(String userUid, String subscriberUid, List<String> pushEmails) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(subscriberUid);
        Objects.requireNonNull(pushEmails);

        User user = userRepository.findOneByUid(userUid);
        DataSubscriber subscriber = dataSubscriberRepository.findOneByUid(subscriberUid);

        validateAdminUser(user, subscriber);
        subscriber.addEmailsForPushNotification(pushEmails);
    }

    @Override
    @Transactional
    public void removePushEmails(String userUid, String subscriberUid, List<String> pushEmails) {
        Objects.requireNonNull(pushEmails);
        Objects.requireNonNull(subscriberUid);

        User user = userRepository.findOneByUid(userUid);

        DataSubscriber subscriber = dataSubscriberRepository.findOneByUid(subscriberUid);
        validateAdminUser(user, subscriber);
        subscriber.removeEmailsForPushNotification(pushEmails);
    }

    @Override
    @Transactional
    public void removeEmailFromAllSubscribers(String pushEmail) {
        logger.info("removing email from subscribers: {}", pushEmail);
        // note: probably need some form of logging
        dataSubscriberRepository.removeEmailFromAllSubscribers(pushEmail);
    }

    @Override
    @Transactional
    public void addUsersWithViewAccess(String adminUid, String subscriberUid, Set<String> userUids) {
        Objects.requireNonNull(adminUid);
        Objects.requireNonNull(subscriberUid);
        Objects.requireNonNull(userUids);

        User user = userRepository.findOneByUid(adminUid);
        DataSubscriber subscriber = dataSubscriberRepository.findOneByUid(subscriberUid);

        validateAdminUser(user, subscriber);
        subscriber.addUserUidsWithAccess(userUids);
        Role liveWireRole = roleRepository.findByNameAndRoleType(liveWireRoleName, Role.RoleType.STANDARD).get(0);
        userRepository.findByUidIn(userUids)
                .forEach(u -> u.addStandardRole(liveWireRole));
    }

    @Override
    @Transactional
    public void removeUsersWithViewAccess(String adminUid, String subscriberUid, Set<String> userUids) {
        Objects.requireNonNull(adminUid);
        Objects.requireNonNull(subscriberUid);
        Objects.requireNonNull(userUids);

        User user = userRepository.findOneByUid(adminUid);
        DataSubscriber subscriber = dataSubscriberRepository.findOneByUid(subscriberUid);

        validateAdminUser(user, subscriber);
        subscriber.removeUserUidsWithAccess(userUids);

        Role liveWireRole = roleRepository.findByNameAndRoleType(liveWireRoleName, Role.RoleType.STANDARD).get(0);
        userRepository.findByUidIn(userUids)
                .forEach(u -> u.removeStandardRole(liveWireRole));
    }

    @Override
    @Transactional
    public void updateSubscriberPermissions(String adminUid, String subscriberUid, boolean canTag, boolean canRelease) {
        Objects.requireNonNull(adminUid);
        Objects.requireNonNull(subscriberUid);

        User user = userRepository.findOneByUid(adminUid);
        DataSubscriber subscriber = dataSubscriberRepository.findOneByUid(subscriberUid);
        validateAdminUser(user, subscriber);

        subscriber.setCanRelease(canRelease);
        subscriber.setCanTag(canTag);
    }

    @Override
    @Transactional
    public void updateSubscriberType(String userUid, String subscriberUid, DataSubscriberType type) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(subscriberUid);

        User user = userRepository.findOneByUid(userUid);
        DataSubscriber subscriber = dataSubscriberRepository.findOneByUid(subscriberUid);
        validateAdminUser(user, subscriber);

        subscriber.setSubscriberType(type);
    }

    @Override
    @Transactional(readOnly = true)
    public int countPushEmails(LiveWireAlert alert) {
        if (alert == null) {
            return dataSubscriberRepository.findAllActiveSubscriberPushEmails(DataSubscriberType.PUBLIC.name()).size();
        } else {
            switch (alert.getDestinationType()) {
                case SINGLE_LIST:
                    return alert.getTargetSubscriber().getPushEmails().size();
                case SINGLE_AND_PUBLIC:
                    return alert.getTargetSubscriber().getPushEmails().size() +
                            dataSubscriberRepository.findAllActiveSubscriberPushEmails(DataSubscriberType.PUBLIC.name()).size();
                case PUBLIC_LIST:
                    return dataSubscriberRepository.findAllActiveSubscriberPushEmails(DataSubscriberType.PUBLIC.name()).size();
                default:
                    return dataSubscriberRepository.findAllActiveSubscriberPushEmails(DataSubscriberType.PUBLIC.name()).size();
            }
        }
    }

    private void validateAdminUser(User user, DataSubscriber subscriber) {
        if (!subscriber.getAdministrator().equals(user)) {
            permissionBroker.validateSystemRole(user, "ROLE_SYSTEM_ADMIN");
        }
    }
}
