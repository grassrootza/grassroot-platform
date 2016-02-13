package za.org.grassroot.services.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.GrassRootCoreConfig;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.LogBookService;
import za.org.grassroot.services.UserManagementService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author Lesetse Kimwaga
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {GrassRootServicesConfig.class, GrassRootCoreConfig.class})
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class LogBookServiceTest extends AbstractTransactionalJUnit4SpringContextTests {

    private static final Logger log = LoggerFactory.getLogger(LogBookServiceTest.class);

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GroupManagementService groupManagementService;

    @Autowired
    LogBookService logBookService;

    @Test
    public void shouldSaveLogBook() {

        User userProfile = userManagementService.createUserProfile(new User("111111111", "aap1"));
        Group level1 = groupManagementService.createNewGroup(userProfile, Arrays.asList("111111112", "111111113"), false);
        LogBook logBook = logBookService.create(userProfile.getId(),level1.getId(),"X must do Y");
        Long fuddyDuddyGroup = 999999999999L;
        LogBook logBookFuddy = logBookService.create(userProfile.getId(),fuddyDuddyGroup,"X must do Y");
        List<LogBook> list = logBookService.getAllLogBookEntriesForGroup(level1.getId());
        assertEquals(1, list.size());
    }

    //@Test
    public void shouldReplicateLogBookEntries() {

        User userProfile = userManagementService.createUserProfile(new User("111111111", "aap1"));

        Group level1 = groupManagementService.createNewGroup(userProfile, Arrays.asList("111111112", "111111113"), false);
        Group level2 = groupManagementService.createSubGroup(userProfile, level1, "level2 group");
        Group level3 = groupManagementService.createSubGroup(userProfile, level2, "level3 group");
        TestTransaction.end();
        TestTransaction.start();

        LogBook logBook = logBookService.create(userProfile.getId(),level1.getId(),"X must do Y",true);
//        TestTransaction.end();
//        TestTransaction.start();
        List<LogBook> list = logBookService.getAllReplicatedEntriesForGroup(level1.getId());
        assertEquals(3, list.size());
        Map groupMap = new HashMap<Long,Long>();
        // check that messages are the same
        for (LogBook lb : list) {
            groupMap.put(lb.getGroupId(),lb.getGroupId());
            assertEquals(logBook.getMessage(),lb.getMessage());
        }
        // check that we have a message for each group
        assertEquals(level1.getId(),groupMap.get(level1.getId()));
        assertEquals(level2.getId(),groupMap.get(level2.getId()));
        assertEquals(level3.getId(),groupMap.get(level3.getId()));

    }


}
