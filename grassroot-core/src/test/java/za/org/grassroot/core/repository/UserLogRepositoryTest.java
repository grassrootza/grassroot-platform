package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.enums.UserLogType;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * Created by luke on 2016/02/22.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class UserLogRepositoryTest {

    private static final Logger log = LoggerFactory.getLogger(UserLogRepositoryTest.class);

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserLogRepository userLogRepository;

    @Test
    public void shouldSaveAndRetrieveUserLogs() {
        assertThat(userLogRepository.count(), is(0L));
        User testUser = userRepository.save(new User("0605551111"));
        UserLog createLog = userLogRepository.save(new UserLog(testUser.getId(), UserLogType.CREATED_IN_DB));
        testUser.setHasInitiatedSession(true);
        testUser.setHasWebProfile(true);
        testUser = userRepository.save(testUser);
        UserLog ussdLog = userLogRepository.save(new UserLog(testUser.getId(), UserLogType.INITIATED_USSD));
        UserLog webLog = userLogRepository.save(new UserLog(testUser.getId(), UserLogType.CREATED_WEB));

        List<UserLog> logsForUser = userLogRepository.findByUserId(testUser.getId());
        assertNotNull(logsForUser);
        assertThat(logsForUser.size(), is(3));
        assertTrue(logsForUser.contains(createLog));
        assertTrue(logsForUser.contains(ussdLog));
        assertTrue(logsForUser.contains(webLog));
        assertThat(userLogRepository.count(), is(3L));
    }

    @Test
    public void shouldRetrieveLogsInInterval() {
        assertThat(userLogRepository.count(), is(0L));
        Timestamp twoMonths = Timestamp.valueOf(LocalDateTime.now().minusMonths(2L));
        Timestamp oneMonth = Timestamp.valueOf(LocalDateTime.now().minusMonths(1L));
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        Sort sort = new Sort(Sort.Direction.ASC, "createdDateTime");

        User testUser = new User("0605550000");
        testUser.setCreatedDateTime(twoMonths);
        testUser = userRepository.save(testUser);
        UserLog createdLog = new UserLog(testUser.getId(), UserLogType.CREATED_IN_DB, "Created the user");
        createdLog.setCreatedDateTime(twoMonths);
        UserLog firstUssd = new UserLog(testUser.getId(), UserLogType.INITIATED_USSD, "First USSD session");
        firstUssd.setCreatedDateTime(oneMonth);
        UserLog createdWeb = new UserLog(testUser.getId(), UserLogType.CREATED_WEB, "Created web profile");
        createdWeb.setCreatedDateTime(now);

        createdLog = userLogRepository.save(createdLog);
        firstUssd = userLogRepository.save(firstUssd);
        createdWeb = userLogRepository.save(createdWeb);

        List<UserLog> getAll = userLogRepository.findByUserId(testUser.getId());
        List<UserLog> createdLogs = userLogRepository.findByUserLogType(UserLogType.CREATED_IN_DB);
        List<UserLog> typeUssd = userLogRepository.findByUserLogType(UserLogType.INITIATED_USSD);
        List<UserLog> typeWeb = userLogRepository.findByUserLogType(UserLogType.CREATED_WEB);

        List<UserLog> oneMonthAgo = userLogRepository.findByUserIdAndCreatedDateTimeBetween(
                testUser.getId(), Timestamp.valueOf(LocalDateTime.now().minusDays(45L)),
                Timestamp.valueOf(LocalDateTime.now().minusDays(15L)), sort);

        assertThat(createdLogs.get(0), is(createdLog));
        assertThat(createdLogs.size(), is(1));
        assertThat(typeUssd.get(0), is(firstUssd));
        assertThat(typeUssd.size(), is(1));
        assertThat(typeWeb.get(0), is(createdWeb));
        assertThat(typeWeb.size(), is(1));

        assertThat(oneMonthAgo, is(typeUssd));
        assertThat(getAll.size(), is(3));

    }

}
