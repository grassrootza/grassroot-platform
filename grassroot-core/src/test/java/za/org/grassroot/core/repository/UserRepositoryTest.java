package za.org.grassroot.core.repository;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.UserRepository;

/**
 * @author Lesetse Kimwaga
 */
import javax.transaction.Transactional;

import java.util.NoSuchElementException;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
public class UserRepositoryTest {

    private Logger log = Logger.getLogger(getClass().getName());


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

    @Test(expected = RuntimeException.class)
    public void shouldNotSaveDuplicatePhoneNumbersInUserTable() throws Exception {
        assertThat(userRepository.count(), is(0L));

        User firstUserToCreate = new User();
        firstUserToCreate.setPhoneNumber("12345");
        firstUserToCreate = userRepository.save(firstUserToCreate);


        User userFromDb = userRepository.findAll().iterator().next();
        assertThat(userFromDb.getId(), is(firstUserToCreate.getId()));
        assertThat(userFromDb.getPhoneNumber(), is("12345"));


        User firstUserFromDB = userRepository.findAll().iterator().next();

        User secondUserToCreate = new User();
        secondUserToCreate.setPhoneNumber("12345");

        userRepository.save(secondUserToCreate);
        fail("Saving a user with the phone number of an already existing user should throw an exception");


    }

    @Test
    public void shouldSaveAndFindByPhoneNumber() throws Exception {
        User userToCreate = new User();
        userToCreate.setPhoneNumber("54321");
        assertNull(userToCreate.getId());
        assertNull(userToCreate.getCreatedDateTime());
        User savedUser = userRepository.save(userToCreate);
        Assert.assertNotEquals(Long.valueOf(0), savedUser.getId());
        User foundUser = userRepository.findByPhoneNumber("54321").iterator().next();
        assertEquals(savedUser.getPhoneNumber(), foundUser.getPhoneNumber());
    }

    @Test(expected = NoSuchElementException.class)
    public void shouldNotFindPhoneNumber() throws Exception {
        User dbUser = userRepository.findByPhoneNumber("99999999999").iterator().next();
    }

    @Test
    public void shouldNotExist() {
        assertEquals(false, userRepository.existsByPhoneNumber("99999999999"));
    }

    @Test
    public void shouldExist() {
        userRepository.save(new User("4444444"));
        assertEquals(true, userRepository.existsByPhoneNumber("4444444"));
    }

}
