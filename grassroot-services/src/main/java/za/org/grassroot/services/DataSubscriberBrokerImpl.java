package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.DataSubscriber;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.DataSubscriberRepository;
import za.org.grassroot.core.repository.UserRepository;

/**
 * Created by luke on 2017/05/05.
 */
@Service
public class DataSubscriberBrokerImpl implements DataSubscriberBroker {

    private final DataSubscriberRepository dataSubscriberRepository;
    private final UserRepository userRepository;
    private final PermissionBroker permissionBroker;

    @Autowired
    public DataSubscriberBrokerImpl(DataSubscriberRepository dataSubscriberRepository, UserRepository userRepository, PermissionBroker permissionBroker) {
        this.dataSubscriberRepository = dataSubscriberRepository;
        this.userRepository = userRepository;
        this.permissionBroker = permissionBroker;
    }

    @Override
    @Transactional
    public void create(final String sysAdminUid, final String displayName, final String primaryEmail) {
        User sysAdmin = userRepository.findOneByUid(sysAdminUid);
        permissionBroker.validateSystemRole(sysAdmin, "ROLE_SYSTEM_ADMIN");

        DataSubscriber subscriber = new DataSubscriber(sysAdmin, sysAdmin, displayName, primaryEmail);
        dataSubscriberRepository.save(subscriber);
    }
}
