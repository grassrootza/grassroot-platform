package za.org.grassroot.meeting_organizer.service.repository;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.meeting_organizer.Application;
import za.org.grassroot.meeting_organizer.DbUnitConfig;
import za.org.grassroot.meeting_organizer.model.User;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {Application.class, DbUnitConfig.class})
@TestExecutionListeners( listeners = DbUnitTestExecutionListener.class, mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
@DatabaseSetup("/db/empty_tables.xml") //init the database with empty tables before each test
public class UserRepositoryIT {

    @Autowired
    UserRepository userRepository;

    @Test
    public void shouldSaveAndRetrieveUserData() throws Exception {
        assertThat(userRepository.count(), is(0L));

        User userToCreate = new User();
        userToCreate.setPhoneNumber("12345");
        assertNull(userToCreate.getId());
        assertNull(userToCreate.getCreatedDateTime());
        userRepository.save(userToCreate);

        assertThat(userRepository.count(), is(1L));
        User userFromDb = userRepository.findAll().iterator().next();
        assertNotNull(userFromDb.getId());
        assertThat(userFromDb.getPhoneNumber(), is("12345"));
        assertNotNull(userFromDb.getCreatedDateTime());
    }

    @Test
    public void shouldNotSaveDuplicatePhoneNumbersInUserTable() throws Exception {
        assertThat(userRepository.count(), is(0L));

        User firstUserToCreate = new User();
        firstUserToCreate.setPhoneNumber("12345");
        userRepository.save(firstUserToCreate);

        User firstUserFromDB = userRepository.findAll().iterator().next();

        User secondUserToCreate = new User();
        secondUserToCreate.setPhoneNumber("12345");
        try {
            userRepository.save(secondUserToCreate);
            fail("Saving a user with the phone number of an already existing user should throw an exception");
        } catch (Exception e) {
            //Doesn't really matter what exception is thrown, as long as the write fails
        }

        assertThat(userRepository.count(), is(1L));
        User userFromDb = userRepository.findAll().iterator().next();
        assertThat(userFromDb.getId(), is(firstUserFromDB.getId()));
        assertThat(userFromDb.getPhoneNumber(), is("12345"));
        assertThat(userFromDb.getCreatedDateTime(), is(firstUserFromDB.getCreatedDateTime()));
    }
}
