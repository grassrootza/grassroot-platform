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

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author Lesetse Kimwaga
 */

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class UserRepositoryTest {

    private static final Logger log = LoggerFactory.getLogger(UserRepositoryTest.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private RoleRepository roleRepository;

    private static final String number = "0821234560";

    @Test
    public void shouldSaveAndRetrieveUserData() throws Exception {
        assertThat(userRepository.count(), is(0L));

        User userToCreate = new User("12345");
        assertNull(userToCreate.getId());
        assertNotNull(userToCreate.getUid());
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

        User firstUserToCreate = new User("12345");
        firstUserToCreate = userRepository.save(firstUserToCreate);


        User userFromDb = userRepository.findAll().iterator().next();
        assertThat(userFromDb.getId(), is(firstUserToCreate.getId()));
        assertThat(userFromDb.getPhoneNumber(), is("12345"));


        User secondUserToCreate = new User("12345");

        userRepository.save(secondUserToCreate);
        fail("Saving a user with the phone number of an already existing user should throw an exception");


    }

    @Test
    public void shouldSaveAndFindByPhoneNumber() throws Exception {
        User userToCreate = new User("54321");
        assertNull(userToCreate.getId());
        assertNotNull(userToCreate.getUid());
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
        group.addMember(u2);
        group = groupRepository.save(group);
        Event event = eventRepository.save(new Meeting("rsvp event", Instant.now(), u1, group, "someLocation", true));
        EventLog eventLog = eventLogRepository.save(new EventLog(u1, event, EventLogType.RSVP, EventRSVPResponse.YES));
        List<User> list = userRepository.findUsersThatRSVPYesForEvent(event);
        log.info("list.size..." + list.size() + "...first user..." + list.get(0).getPhoneNumber());
        assertEquals(u1.getPhoneNumber(),list.get(0).getPhoneNumber());
    }

    @Test
    public void shouldReturnUserThatRSVPNo() {
        User u1 = userRepository.save(new User("0821234570"));
        User u2 = userRepository.save(new User("0821234571"));

        Group group = groupRepository.save(new Group("rsvp yes",u1));
        group.addMember(u2);
        group = groupRepository.save(group);

        Event event = eventRepository.save(new Meeting("rsvp event", Instant.now(), u1, group, "someLocation", true));
		eventLogRepository.save(new EventLog(u1, event, EventLogType.RSVP, EventRSVPResponse.YES));
        eventLogRepository.save(new EventLog(u2, event, EventLogType.RSVP, EventRSVPResponse.NO));

        List<User> list = userRepository.findUsersThatRSVPNoForEvent(event);
        assertEquals(u2.getPhoneNumber(),list.get(0).getPhoneNumber());
    }

    @Test
    @Rollback
    public void shouldSaveAddedRole() {
        User user = userRepository.save(new User(number));
        Role role = roleRepository.save(new Role("TEST_ROLE", null));
        user.addStandardRole(role);
        userRepository.save(user);
        User userFromDb = userRepository.findByPhoneNumber(number);
        assertNotNull(userFromDb);
        assertEquals(userFromDb.getId(), user.getId());
        assertTrue(userFromDb.getStandardRoles().contains(role));
    }

    @Test
    @Rollback
    public void shouldRemoveRole() {
        User user = userRepository.save(new User(number));
        Role role = roleRepository.save(new Role("TEST_ROLE", null));
        user.addStandardRole(role);
        user = userRepository.save(user);
        assertTrue(user.getStandardRoles().contains(role));
        user.removeStandardRole(role);
        userRepository.save(user);
        User userfromDb = userRepository.findByPhoneNumber(number);
        assertNotNull(userfromDb);
        assertEquals(userfromDb.getId(), user.getId());
        assertFalse(userfromDb.getStandardRoles().contains(role));
    }

    @Test
    @Rollback
    public void shouldFindUsersByStringsAndGroup() {
        assertThat(userRepository.count(), is(0L));

        String phoneBase = "080111000";

        User user1 = userRepository.save(new User(phoneBase + "1", "tester1"));
        User user2 = userRepository.save(new User(phoneBase + "2", "anonymous"));
        User user3 = userRepository.save(new User(phoneBase + "3", "tester2"));
        User user4 = userRepository.save(new User("0701110001", "tester3"));
        User user5 = userRepository.save(new User("0701110002", "no name"));

        Group testGroup = groupRepository.save(new Group("test group", user1));
        testGroup.addMember(user1);
        testGroup.addMember(user2);
        testGroup = groupRepository.save(testGroup);

        List<User> usersByPhone = userRepository.findByPhoneNumberContaining(phoneBase);
        List<User> usersByDisplay = userRepository.findByDisplayNameContaining("tester");
        List<User> usersByBoth = userRepository.findByDisplayNameContainingOrPhoneNumberContaining("tester", phoneBase);
        List<User> usersByBothAndGroup = userRepository.
                findByGroupsPartOfAndDisplayNameContainingOrPhoneNumberContaining(testGroup, "tester", "tester");

        assertThat(userRepository.count(), is(5L));
        assertFalse(usersByPhone.isEmpty());
        assertFalse(usersByDisplay.isEmpty());
        assertFalse(usersByBoth.isEmpty());
        assertFalse(usersByBothAndGroup.isEmpty());

        assertThat(usersByPhone.size(), is(3));
        assertThat(usersByDisplay.size(), is(3));
        assertThat(usersByBoth.size(), is(4));
        assertThat(usersByBothAndGroup.size(), is(1));

        assertTrue(usersByPhone.containsAll(Arrays.asList(user1, user2, user3)));
        assertTrue(usersByDisplay.containsAll(Arrays.asList(user1, user3, user4)));
        assertTrue(usersByBoth.containsAll(Arrays.asList(user1, user2, user3, user4)));
        assertTrue(usersByBothAndGroup.contains(user1));

        assertFalse(usersByPhone.contains(user4));
        assertFalse(usersByDisplay.contains(user2));
        assertFalse(usersByBoth.contains(user5));
        assertFalse(usersByBothAndGroup.contains(user2));

    }

    @Test
    public void shouldFindGroupMembersExcludingCreator() {
        String phoneBase = "080555000";

        User testUser = userRepository.save(new User(phoneBase +" 1", "tester1"));
        User user2 = userRepository.save(new User(phoneBase + "2", "anonymous"));
        User user3 = userRepository.save(new User(phoneBase + "3", "tester2"));

        Group testGroup = groupRepository.save(new Group("tg1", testUser));
        testGroup.addMember(testUser);
        testGroup.addMember(user2);
        testGroup.addMember(user3);
        Group testGroupFromDb = groupRepository.save(testGroup);

        assertThat(testGroupFromDb.getMemberships().size(), is(3));
        assertTrue(testGroupFromDb.hasMember(testUser));
        assertTrue(testGroupFromDb.hasMember(user2));
        assertTrue(testGroupFromDb.hasMember(user3));

        List<User> nonCreatorMembers = userRepository.findByGroupsPartOfAndIdNot(testGroupFromDb, testUser.getId());

        assertThat(nonCreatorMembers.size(), is(2));
        assertFalse(nonCreatorMembers.contains(testUser));
        assertTrue(nonCreatorMembers.contains(user2));
        assertTrue(nonCreatorMembers.contains(user3));

    }

}
