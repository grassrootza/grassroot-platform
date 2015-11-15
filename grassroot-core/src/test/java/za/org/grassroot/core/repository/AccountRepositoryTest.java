package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Account;

import javax.transaction.Transactional;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author Lesetse Kimwaga
=======

import javax.transaction.Transactional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * Created by luke on 2015/11/14.
>>>>>>> Stashed changes
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class AccountRepositoryTest {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(AccountRepositoryTest.class);

    private static final String accountName = "Paying institution";
    private static final String billingEmail = "accounts@institution.com";

    @Autowired
    AccountRepository accountRepository;

    @Test
    public void shouldSaveAndReturnId() {

        assertThat(accountRepository.count(), is(0L));

        Account account = new Account("accountname",true);
        account = accountRepository.save(account);
        assertNotEquals(null,account.getId());

        assertEquals(Long.parseLong("2"),Long.parseLong(account.getId().toString())); // looks like not clearing cache, hence
    }

    @Test
    public void shouldCreateAndSaveAccount() {

        assertThat(accountRepository.count(), is(0L));

        Account account = new Account(accountName, true);
        accountRepository.save(account);

        assertThat(accountRepository.count(), is(1L));

        Account accountFromDb = accountRepository.findAll().iterator().next();

        assertNotNull(accountFromDb.getId());
        assertNotNull(accountFromDb.getCreatedDateTime());

        assertThat(accountFromDb.getAccountName(), is(accountName));
        assertTrue(accountFromDb.isEnabled());
    }

    @Test
    public void shouldSetBillingAddress() {

        assertThat(accountRepository.count(), is(0L));

        Account account = new Account(accountName, true);
        accountRepository.save(account);

        assertThat(accountRepository.count(), is(1L));

        Account accountFromDb = accountRepository.findAll().iterator().next();
        accountFromDb.setPrimaryEmail(billingEmail);
        accountFromDb = accountRepository.save(accountFromDb);

        assertNotNull(accountFromDb.getId());
        assertNotNull(accountFromDb.getCreatedDateTime());
        assertThat(accountFromDb.getAccountName(), is(accountName));
        assertThat(accountFromDb.getPrimaryEmail(), is(billingEmail));

    }

    @Test
    public void shouldFindByAccountName() {

        assertThat(accountRepository.count(), is(0L));

    }

    @Test
    public void shouldFindByBillingMail() {

        assertThat(accountRepository.count(), is(0L));

    }

    @Test
    public void shouldDisable() {

        assertThat(accountRepository.count(), is(0L));

    }

}
