package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.springframework.data.jpa.domain.Specifications.*;
import static za.org.grassroot.core.enums.UserInterfaceType.UNKNOWN;
import static za.org.grassroot.core.enums.UserLogType.*;
import static za.org.grassroot.core.specifications.UserLogSpecifications.*;

/**
 * Created by luke on 2016/02/22.
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class UserLogRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserLogRepository userLogRepository;

    @Test
    public void shouldSaveAndRetrieveUserLogs() {
        assertThat(userLogRepository.count(), is(0L));
        User testUser = userRepository.save(new User("0605551111", null, null));
        UserLog createLog = userLogRepository.save(new UserLog(testUser.getUid(), CREATED_IN_DB, null, UNKNOWN));
        testUser.setHasInitiatedSession(true);
        testUser.setHasWebProfile(true);
        testUser = userRepository.save(testUser);
        UserLog ussdLog = userLogRepository.save(new UserLog(testUser.getUid(), INITIATED_USSD, null, UNKNOWN));
        UserLog webLog = userLogRepository.save(new UserLog(testUser.getUid(), CREATED_WEB, null, UNKNOWN));

        List<UserLog> logsForUser = userLogRepository.findAll(where(
                forUser(testUser)));
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
        Instant twoMonths = DateTimeUtil.convertToSystemTime(LocalDateTime.now().minusMonths(2L), DateTimeUtil.getSAST());
        Instant oneMonth = DateTimeUtil.convertToSystemTime(LocalDateTime.now().minusMonths(1L), DateTimeUtil.getSAST());
        Instant now = Instant.now();
        Sort sort = new Sort(Sort.Direction.ASC, "creationTime");

        User testUser = new User("0605550000", null, null);
        testUser = userRepository.save(testUser);
        UserLog createdLog = new UserLog(testUser.getUid(), CREATED_IN_DB, "Created the user", UNKNOWN);
        createdLog.setCreationTime(twoMonths);
        UserLog firstUssd = new UserLog(testUser.getUid(), INITIATED_USSD, "First USSD session", UNKNOWN);
        firstUssd.setCreationTime(oneMonth);
        UserLog createdWeb = new UserLog(testUser.getUid(), CREATED_WEB, "Created web profile", UNKNOWN);
        createdWeb.setCreationTime(now);

        createdLog = userLogRepository.save(createdLog);
        firstUssd = userLogRepository.save(firstUssd);
        createdWeb = userLogRepository.save(createdWeb);

        List<UserLog> getAll = userLogRepository.findAll(where(forUser(testUser)));
        List<UserLog> createdLogs = userLogRepository.findAll(where(ofType(CREATED_IN_DB)));
        List<UserLog> typeUssd = userLogRepository.findAll(where(ofType(INITIATED_USSD)));
        List<UserLog> typeWeb = userLogRepository.findAll(where(ofType(CREATED_WEB)));

        List<UserLog> oneMonthAgo = userLogRepository.findAll(where(forUser(testUser))
                        .and(creationTimeBetween(LocalDateTime.now().minusDays(45L).toInstant(ZoneOffset.UTC),
                LocalDateTime.now().minusDays(15L).toInstant(ZoneOffset.UTC))), sort);

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
