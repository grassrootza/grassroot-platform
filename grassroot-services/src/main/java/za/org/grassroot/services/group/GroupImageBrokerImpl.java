package za.org.grassroot.services.group;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.GroupDefaultImage;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.util.Objects;

/**
 * Created by luke on 2016/09/26.
 */
@Service
public class GroupImageBrokerImpl implements GroupImageBroker {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private PermissionBroker permissionBroker;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private LogsAndNotificationsBroker logsAndNotificationsBroker;

    @Override
    @Transactional
    public void saveGroupImage(String userUid, String groupUid, String imageUrl, byte[] image) {
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(imageUrl);
        Objects.requireNonNull(image);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);
        group.setImage(image);
        group.setImageUrl(imageUrl);

        logAfterCommit(new GroupLog(group, user, GroupLogType.GROUP_AVATAR_UPLOADED, group.getId(), "Group avatar uploaded"));
    }

    @Override
    public Group getGroupByImageUrl(String imageUrl) {
        return groupRepository.findOne(Specifications.where(GroupSpecifications.hasImageUrl(imageUrl)));
    }

    @Override
    @Transactional
    public void setGroupImageToDefault(String userUid, String groupUid, GroupDefaultImage defaultImage, boolean removeCustomImage) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(defaultImage);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        group.setDefaultImage(defaultImage);

        if (removeCustomImage) {
            group.setImage(null);
            group.setImageUrl(null);
        }

        logAfterCommit(new GroupLog(group, user, GroupLogType.GROUP_DEFAULT_IMAGE_CHANGED,
                group.getId(), defaultImage.toString()));
    }

    private void logAfterCommit(GroupLog groupLog) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(groupLog);
        AfterTxCommitTask afterTxCommitTask = () -> logsAndNotificationsBroker.asyncStoreBundle(bundle);
        applicationEventPublisher.publishEvent(afterTxCommitTask);
    }

}
