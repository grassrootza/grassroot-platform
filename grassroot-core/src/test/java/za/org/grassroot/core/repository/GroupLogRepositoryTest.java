package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.GroupLogType;

import javax.transaction.Transactional;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author Luke Jordan
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class GroupLogRepositoryTest {

    private Logger log = Logger.getLogger(getClass().getName());

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GroupLogRepository groupLogRepository;


    @Test
    public void shouldSaveAndRetrieveGroupLog() throws Exception {

        assertThat(groupLogRepository.count(), is(0L));

        User userToDoTests = new User("0810001111", null, null);
        userToDoTests = userRepository.save(userToDoTests);

        Group groupToCreate = new Group("testGroup", userToDoTests);
        groupToCreate = groupRepository.save(groupToCreate);

        GroupLog groupLog = new GroupLog(groupToCreate, userToDoTests, GroupLogType.GROUP_ADDED, null);
        assertNull(groupLog.getId());
        groupLogRepository.save(groupLog);

        assertThat(groupLogRepository.count(), is(1l));
        GroupLog groupLogFromDb = groupLogRepository.findByGroup(groupToCreate).iterator().next();
        assertNotNull(groupLogFromDb.getId());
        assertNotNull(groupLogFromDb.getCreatedDateTime());
        assertThat(groupLogFromDb.getGroup(), is(groupToCreate));
        assertThat(groupLogFromDb.getUser(), is(userToDoTests));
    }

    @Test
    public void shouldReturnOnlyMostRecentLog() throws Exception {

        assertThat(groupLogRepository.count(), is(0L));

        User userToDoTests = userRepository.save(new User("0810002222", null, null));
        Group groupToCreate = groupRepository.save(new Group("testGroup", userToDoTests));

        GroupLog groupLog1 = new GroupLog(groupToCreate, userToDoTests, GroupLogType.GROUP_ADDED, null);
        assertNull(groupLog1.getId());
        groupLogRepository.save(groupLog1);

        GroupLog groupLog2 = new GroupLog(groupToCreate, userToDoTests, GroupLogType.GROUP_MEMBER_ADDED,
                userToDoTests, null, null, null);
        assertNull(groupLog2.getId());
        groupLogRepository.save(groupLog2);

        assertThat(groupLogRepository.count(), is(2l));
        GroupLog groupLogFromDb = groupLogRepository.findFirstByGroupOrderByCreatedDateTimeDesc(groupToCreate);
        assertNotNull(groupLogFromDb.getId());
        assertNotNull(groupLogFromDb.getCreatedDateTime());
        assertThat(groupLogFromDb.getGroup(), is(groupToCreate));
        assertThat(groupLogFromDb.getUser(), is(userToDoTests));

    }



}


