package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import javax.transaction.Transactional;
import java.util.List;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author Lesetse Kimwaga
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class AccountRepositoryTest {

    private Logger log = Logger.getLogger(getClass().getName());

    @Autowired
    AccountRepository accountRepository;


    @Test
    public void shouldSaveAndReturnId() {
        Account account = new Account("accountname",true);
        account = accountRepository.save(account);
        assertNotEquals(null,account.getId());
        assertEquals(Long.parseLong("1"),Long.parseLong(account.getId().toString()));
    }
}


