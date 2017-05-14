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
import za.org.grassroot.core.repository.DataSubscriberRepository;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.PermissionBroker;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
    @Transactional
    public void create(final String sysAdminUid, final String displayName, final String primaryEmail, boolean addPrimaryEmailToPush, List<String> additionalPushEmails, boolean active) {
        Objects.requireNonNull(sysAdminUid);
        Objects.requireNonNull(displayName);

        logger.info("Creating LiveWire subscriber, with params: addPrimaryEmail: {}, additionalMails: {}, active: {}",
                addPrimaryEmailToPush, additionalPushEmails, active);

        User sysAdmin = userRepository.findOneByUid(sysAdminUid);
        permissionBroker.validateSystemRole(sysAdmin, "ROLE_SYSTEM_ADMIN");

        DataSubscriber subscriber = new DataSubscriber(sysAdmin, sysAdmin, displayName, primaryEmail, active);

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
        Objects.requireNonNull(subscriberUid);
        Objects.requireNonNull(pushEmails);

        User user = userRepository.findOneByUid(userUid);
        DataSubscriber subscriber = dataSubscriberRepository.findOneByUid(subscriberUid);

        validateAdminUser(user, subscriber);
        subscriber.removeEmailsForPushNotification(pushEmails);
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
    @Transactional(readOnly = true)
    public int countPushEmails() {
        return dataSubscriberRepository.findAllActiveSubscriberPushEmails().size();
    }

    private void validateAdminUser(User user, DataSubscriber subscriber) {
        if (!subscriber.getAdministrator().equals(user)) {
            permissionBroker.validateSystemRole(user, "ROLE_SYSTEM_ADMIN");
        }
    }
}
