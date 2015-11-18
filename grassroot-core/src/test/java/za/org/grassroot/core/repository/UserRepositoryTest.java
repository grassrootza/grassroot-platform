package za.org.grassroot.core.repository;

import org.junit.Assert;
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
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;

/**
 * @author Lesetse Kimwaga
 */
import javax.transaction.Transactional;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class UserRepositoryTest {

    private static final Logger log = LoggerFactory.getLogger(UserRepositoryTest.class);

    @Autowired
    UserRepository userRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    EventLogRepository eventLogRepository;

    @Autowired
    RoleRepository roleRepository;

    private static final String number = "0821234560";

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
        User foundUser = userRepository.findByPhoneNumber("54321");
        assertEquals(savedUser.getPhoneNumber(), foundUser.getPhoneNumber());
    }

    @Test
    public void shouldNotFindPhoneNumber() throws Exception {
        User dbUser = userRepository.findByPhoneNumber("99999999999");
        assertNull(dbUser);
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

    @Test
    public void shouldReturnUserThatRSVPYes() {
        User u1 = userRepository.save(new User("0821234560"));
        User u2 = userRepository.save(new User("0821234561"));
        Group group = groupRepository.save(new Group("rsvp yes",u1));
        group.getGroupMembers().add(u2);
        group = groupRepository.save(group);
        Event event = eventRepository.save(new Event("rsvp event",u1,group,true));
        EventLog eventLog = eventLogRepository.save(new EventLog(u1,event, EventLogType.EventRSVP, EventRSVPResponse.YES.toString()));
        List<User> list = userRepository.findUsersThatRSVPYesForEvent(event);
        log.info("list.size..." + list.size() + "...first user..." + list.get(0).getPhoneNumber());
        assertEquals(u1.getPhoneNumber(),list.get(0).getPhoneNumber());
    }

    @Test
    public void shouldReturnUserThatRSVPNo() {
        User u1 = userRepository.save(new User("0821234570"));
        User u2 = userRepository.save(new User("0821234571"));
        Group group = groupRepository.save(new Group("rsvp yes",u1));
        group.getGroupMembers().add(u2);
        group = groupRepository.save(group);
        Event event = eventRepository.save(new Event("rsvp event",u1,group,true));
        EventLog eventLog = eventLogRepository.save(new EventLog(u1,event, EventLogType.EventRSVP, EventRSVPResponse.YES.toString()));
        EventLog eventLog2 = eventLogRepository.save(new EventLog(u2,event, EventLogType.EventRSVP, EventRSVPResponse.NO.toString()));

        List<User> list = userRepository.findUsersThatRSVPNoForEvent(event);
        assertEquals(u2.getPhoneNumber(),list.get(0).getPhoneNumber());
    }

    @Test
    @Rollback
    public void shouldSaveAddedRole() {
        User user = userRepository.save(new User(number));
        Role role = roleRepository.save(new Role("TEST_ROLE"));
        user.addRole(role);
        userRepository.save(user);
        User userFromDb = userRepository.findByPhoneNumber(number);
        assertNotNull(userFromDb);
        assertEquals(userFromDb.getId(), user.getId());
        assertTrue(userFromDb.getRoles().contains(role));
    }

    @Test
    @Rollback
    public void shouldRemoveRole() {
        User user = userRepository.save(new User(number));
        Role role = roleRepository.save(new Role("TEST_ROLE"));
        user.addRole(role);
        user = userRepository.save(user);
        assertTrue(user.getRoles().contains(role));
        user.removeRole(role);
        userRepository.save(user);
        User userfromDb = userRepository.findByPhoneNumber(number);
        assertNotNull(userfromDb);
        assertEquals(userfromDb.getId(), user.getId());
        assertFalse(userfromDb.getRoles().contains(role));
    }

}
