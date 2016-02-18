package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.NewGroupMember;
import za.org.grassroot.core.enums.EventChangeType;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;

/**
 * Created by luke on 2016/02/15.
 */
@Service
@Transactional
@Lazy
public class AsyncGroupManager implements AsyncGroupService {

    private static final Logger log = LoggerFactory.getLogger(AsyncGroupManager.class);
    private static final Long dontKnowTheUser = 0L;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupLogRepository groupLogRepository;

    @Autowired
    private GenericJmsTemplateProducerService jmsTemplateProducerService;

    @Async
    @Override
    public void recordGroupLog(Long groupId, Long userDoingId, GroupLogType type, Long userOrGroupAffectedId, String description) {
        groupLogRepository.save(new GroupLog(groupId, userDoingId, type, userOrGroupAffectedId, description));
    }

    @Async
    @Override
    public void addNewGroupMemberLogsMessages(Group group, User newMember, Long addingUserId) {

        if (hasDefaultLanguage(group) && !newMember.isHasInitiatedSession())
            assignDefaultLanguage(group, newMember);

        Long savingUserId = (addingUserId == null) ? dontKnowTheUser : addingUserId;

        groupLogRepository.save(new GroupLog(group.getId(), savingUserId, GroupLogType.GROUP_MEMBER_ADDED, newMember.getId()));
        jmsTemplateProducerService.sendWithNoReply(EventChangeType.USER_ADDED.toString(), new NewGroupMember(group, newMember));
    }

    @Async
    @Override
    public void removeGroupMemberLogs(Group group, User oldMember, User removingUser) {
        Long removingUserId = (removingUser == null) ? dontKnowTheUser : removingUser.getId();
        String description = (oldMember.getId() == removingUserId) ? "Unsubscribed" : "Removed from group";
        groupLogRepository.save(new GroupLog(group.getId(), removingUserId, GroupLogType.GROUP_MEMBER_REMOVED,
                                             oldMember.getId(), description));
    }

    @Async
    @Override
    public void assignDefaultLanguage(Group group, User user) {
        user.setLanguageCode(group.getDefaultLanguage());
        userRepository.save(user);
    }

    @Override
    public boolean hasDefaultLanguage(Group group) {
        return (group.getDefaultLanguage() != null && !group.getDefaultLanguage().trim().equals("en"));
    }


}
