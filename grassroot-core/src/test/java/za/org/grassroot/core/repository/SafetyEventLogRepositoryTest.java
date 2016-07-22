package za.org.grassroot.core.repository;

import org.hibernate.mapping.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.SafetyEventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.SafetyEventLogType;

import static org.junit.Assert.assertNotNull;

/**
 * Created by paballo on 2016/07/21.
 */

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class SafetyEventLogRepositoryTest {

    @Autowired
    private SafetyEventRepository safetyEventRepository;

    @Autowired
    private SafetyEventLogRepository safetyEventLogRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;


    @Test
    public void shouldFindBySafetyEventAndUser() throws  Exception{

        User user = userRepository.save(new User("0838350961", "user"));
        Group group = groupRepository.save(new Group("group", user));
        SafetyEvent safetyEvent = safetyEventRepository.save(new SafetyEvent(user,group));
        safetyEventLogRepository.save(new SafetyEventLog(user,safetyEvent, SafetyEventLogType.ACTIVATED,false,null));
        SafetyEventLog safetyEventLog = safetyEventLogRepository.findByUserAndSafetyEvent(user,safetyEvent);
        assertNotNull(safetyEventLog);
        assertNotNull(safetyEventLog.getUid());
        assertNotNull(safetyEventLog.getCreatedDateTime());
    }
}
