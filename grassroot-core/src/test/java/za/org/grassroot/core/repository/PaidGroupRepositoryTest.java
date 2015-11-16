package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.PaidGroup;
import za.org.grassroot.core.domain.User;

import javax.transaction.Transactional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;


/**
 * Created by luke on 2015/11/16.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class PaidGroupRepositoryTest {

    private static final Logger log = LoggerFactory.getLogger(PaidGroupRepositoryTest.class);

    @Autowired
    PaidGroupRepository paidGroupRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    AccountRepository accountRepository;

    @Test
    @Rollback
    public void shouldSaveAndReturnId() {

        assertThat(paidGroupRepository.count(), is(0L));

        PaidGroup paidGroup = new PaidGroup();
        paidGroup = paidGroupRepository.save(paidGroup);

        assertNotEquals(null, paidGroup.getId());

        // assertEquals(Long.parseLong("1"), Long.parseLong(paidGroup.getId().toString())); // DB is not rolling back so these are causing errors
    }

    @Test
    @Rollback
    public void shouldSaveWithGroupUserAccount() {

        assertThat(paidGroupRepository.count(), is(0L));

        User testUser = new User("0810005555");
        testUser = userRepository.save(testUser);
        Group testGroup = new Group("testGroup", testUser);
        testGroup = groupRepository.save(testGroup);
        Account testAccount = new Account("testAccount", true);
        testAccount = accountRepository.save(testAccount);

        PaidGroup paidGroup = new PaidGroup(testGroup, testAccount, testUser);
        paidGroup = paidGroupRepository.save(paidGroup);

    }

}
