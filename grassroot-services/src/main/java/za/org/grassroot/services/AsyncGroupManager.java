package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
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

import java.util.List;
import java.util.concurrent.Future;

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
    private GroupRepository groupRepository;

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
    public void assignDefaultLanguage(Group group, User user) {
        user.setLanguageCode(group.getDefaultLanguage());
        userRepository.save(user);
    }

    @Async
    @Override
    @Transactional
    public Future<Group> addMembersWithoutRoles(Long groupId, List<User> newMembers) {
        Group group = groupRepository.findOne(groupId);
        userRepository.save(newMembers);
        group.addMembers(newMembers);
        Group savedGroup = groupRepository.saveAndFlush(group);
        return new AsyncResult<>(savedGroup);
    }

    @Override
    public boolean hasDefaultLanguage(Group group) {
        return (group.getDefaultLanguage() != null && !group.getDefaultLanguage().trim().equals("en"));
    }


}
