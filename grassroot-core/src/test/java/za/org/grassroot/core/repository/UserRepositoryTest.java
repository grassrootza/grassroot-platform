package za.org.grassroot.core.repository;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.StandaloneDatabaseConfig;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.MeetingBuilder;
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.util.PhoneNumberUtil;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author Lesetse Kimwaga
 */

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {StandaloneDatabaseConfig.class})
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
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
    public void shouldCheckAndroidProfile() {
        assertThat(userRepository.count(), is(0L));
        User userProfile = new User("12345", null, null);
        assertNull(userProfile.getId());
        assertNotNull(userProfile.getUid());
        userProfile.setHasAndroidProfile(true);
        userRepository.save(userProfile);

        assertThat(userRepository.count(), is(1L));
        User fromDb = userRepository.findAll().iterator().next();
        assertNotNull(fromDb.getUid());
        assertTrue(fromDb.hasAndroidProfile());
    }

    @Test
    public void shouldHaveSafetyGroup() {
        User newUser = new User("0111222", null, null);
        Group newGroup = new Group("test Group", newUser);
        assertNotNull(newUser.getUid());
        newUser.setSafetyGroup(newGroup);
        assertTrue(newUser.hasSafetyGroup());
        assertThat(newUser.getSafetyGroup(), is(newGroup));
        userRepository.save(newUser);
        newUser.setSafetyGroup(null);
        assertFalse(newUser.hasSafetyGroup());

    }

    @Test
    public void shouldUpdateTimeStamps() {
        User userStamp = new User("2567", null, null);
        assertNotNull(userStamp.getUid());
        Instant createdTime = Instant.now();
        userStamp.updateTimeStamps();
        assertTrue(userStamp.getCreatedDateTime().equals(createdTime));
    }

    @Test
    public void shouldSetUserName() {
        User user = new User("9087687", null, null);
        assertNotNull(user.getUid());
        user.setUsername("john");
        assertThat(user.getUsername(), is("john"));
        userRepository.save(user);

        User userDb = userRepository.findOneByUid(user.getUid());
        assertThat(userDb.getUsername(), is("john"));
    }

    @Test
    public void shouldSavePassword() {
        User userPass = new User("21345", null, null);
        assertNotNull(userPass.getUid());
        userPass.setPassword("password");
        assertThat(userPass.getPassword(), is("password"));
        userRepository.save(userPass);

        User userDb = userRepository.findOneByUid(userPass.getUid());
        assertThat(userDb.getPassword(), is("password"));
    }

    @Test
    public void shouldFetchNameToDisplay() {
        User user = new User("0987", null, null);
        assertNotNull(user.getUid());
        user.setDisplayName("john");
        user.setHasSetOwnName(true);
        assertTrue(user.isHasSetOwnName());
        assertThat(user.getDisplayName(), is("john"));
        userRepository.save(user);

        User db = userRepository.findOneByUid(user.getUid());
        assertTrue(user.isHasSetOwnName());
        assertThat(db.getDisplayName(), is("john"));
    }

    @Test
    public void shouldSetFirstNameAndLastName() {
        User userName = new User("09876", null, null);
        assertNotNull(userName.getUid());
        userName.setFirstName("john");
        userName.setLastName("doe");
        assertThat(userName.getFirstName(), is("john"));
        assertThat(userName.getLastName(), is("doe"));
        userRepository.save(userName);

        User userDb = userRepository.findOneByUid(userName.getUid());
        assertThat(userDb.getFirstName(), is("john"));
        assertThat(userDb.getLastName(), is("doe"));

    }

    @Test
    public void shouldSetAccounts() {
        User userAcc = new User("098765", null, null);
        assertNotNull(userAcc.getUid());
        Account account = new Account(userAcc,"", AccountType.FREE,userAcc,
                AccountPaymentType.FREE_TRIAL,AccountBillingCycle.ANNUAL);
        Account account1 = new Account(userAcc,"", AccountType.FREE,userAcc,
                AccountPaymentType.FREE_TRIAL,AccountBillingCycle.MONTHLY);
        Set<Account> accounts = new HashSet<>();
        userAcc.setAccountsAdministered(accounts);
        userAcc.setPrimaryAccount(account);
        userAcc.addAccountAdministered(account);
        userAcc.addAccountAdministered(account1);
        userRepository.save(userAcc);
        assertTrue(userAcc.hasMultipleAccounts());
        assertThat(userAcc.getPrimaryAccount(),is(account));
        assertThat(userAcc.getAccountsAdministered().size(),is(2));

        User userDb = userRepository.findOneByUid(userAcc.getUid());
        assertNotNull(userDb.getUid());
        assertTrue(userAcc.hasMultipleAccounts());
        assertThat(userAcc.getPrimaryAccount(),is(account));
        assertThat(userAcc.getAccountsAdministered().size(),is(2));
    }

    @Test
    public void shouldSetLanguageCode() {
        User userLanguage = new User("12345", null, null);
        assertNotNull(userLanguage.getUid());
        userLanguage.setLanguageCode("EN");
        assertThat(userLanguage.getLanguageCode(), is("EN"));
        userRepository.save(userLanguage);

        User userDb = userRepository.findOneByUid(userLanguage.getUid());
        assertThat(userDb.getLanguageCode(), is("EN"));

    }

    @Test
    public void shouldGetAlertPreference() {
        User newUser = new User("13124", null, null);
        assertNotNull(newUser.getUid());
        newUser.setAlertPreference(AlertPreference.NOTIFY_NEW_AND_REMINDERS);
        assertThat(newUser.getAlertPreference(),is(AlertPreference.NOTIFY_NEW_AND_REMINDERS));
        userRepository.save(newUser);
        User fromDb = userRepository.findAll().iterator().next();
        assertNotNull(fromDb.getUid());
        assertThat(newUser.getAlertPreference(),is(AlertPreference.NOTIFY_NEW_AND_REMINDERS));

    }

    @Test
    public void shouldSavePhoneNumber() {
        User userNumber = new User("907856", null, null);
        assertNotNull(userNumber.getUid());
        userNumber.setPhoneNumber("07890");
        assertThat(userNumber.getPhoneNumber(), is("07890"));
        assertThat(userNumber.getNationalNumber(), is(PhoneNumberUtil.formattedNumber("07890")));
        userRepository.save(userNumber);

        User userDb = userRepository.findByPhoneNumberAndPhoneNumberNotNull("07890");
        assertThat(userDb.getPhoneNumber(), is("07890"));
        assertThat(userDb.getNationalNumber(), is(PhoneNumberUtil.formattedNumber("07890")));

    }

    @Test
    public void shouldSetEmailAddress() {
        User userEmail = new User("09090", null, null);
        assertNotNull(userEmail.getUid());
        userEmail.setEmailAddress("johnDoe@gmail.com");
        assertTrue(userEmail.hasEmailAddress());
        assertThat(userEmail.getEmailAddress(), is("johnDoe@gmail.com"));
        userRepository.save(userEmail);

        User userDb = userRepository.findOneByUid(userEmail.getUid());
        assertTrue(userDb.hasEmailAddress());
        assertThat(userDb.getEmailAddress(), is("johnDoe@gmail.com"));
    }

    @Test
    public void shouldAddAndRemoveMappedMembership() {

        User user = new User("099654", null, null);
        Role role = new Role("", null);
        Group group = new Group("", user);
        Membership newMember = new Membership(group, user, role, Instant.now(), GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        user.addMappedByMembership(newMember);
        assertThat(user.getMemberships().size(), is(1));
        userRepository.save(user);
        user.removeMappedByMembership(newMember);
        assertThat(user.getMemberships().size(), is(0));
        userRepository.save(user);
    }

    @Test
    public void shouldFetchMemberships() {
        User userMember = new User("1234", null, null);
        Group group = new Group("Group", userMember);
        Role role = new Role("", null);
        assertNotNull(userMember.getUid());
        assertTrue(userMember.getMemberships().isEmpty());

        Membership newMember = new Membership(group, userMember, role, Instant.now(), GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        assertThat(newMember.getGroup().getGroupName(), is("Group"));
        assertThat(newMember.getUser().getPhoneNumber(), is("1234"));
        assertThat(userMember.getMemberships().size(),is(0));
    }

    @Test
    public void shouldSaveMessagePreference() {
        User userPrefer = new User("0908", null, null);
        assertNotNull(userPrefer.getUid());
        userPrefer.setMessagingPreference(DeliveryRoute.SMS);
        assertThat(userPrefer.getMessagingPreference(), is(DeliveryRoute.SMS));
        userRepository.save(userPrefer);
        User userDb = userRepository.findOneByUid(userPrefer.getUid());
        assertNotNull(userDb.getUid());
        assertThat(userDb.getMessagingPreference(), is(DeliveryRoute.SMS));

    }

    @Test
    public void shouldSaveNotificationPriority() {
        User userPriority = new User("56790", null, null);
        assertNotNull(userPriority.getUid());
        userPriority.setNotificationPriority(2);
        assertThat(userPriority.getNotificationPriority(), is(2));
        userPriority.setNotificationPriority(null);
        assertThat(userPriority.getNotificationPriority(), is(1));
        userRepository.save(userPriority);
    }

    @Test
    public void checkTrialStatus() throws Exception {

        assertThat(userRepository.count(), is(0L));
        User entity = new User("07515757537", null, null);
        assertNull(entity.getId());
        assertNotNull(entity.getUid());
        userRepository.save(entity);

        assertThat(userRepository.count(), is(1L));
        User db = userRepository.findAll().iterator().next();
        assertNotNull(db.getId());
        assertThat(db.getPhoneNumber(), is("07515757537"));
        assertNotNull(db.getCreatedDateTime());
        assertFalse(db.isHasUsedFreeTrial());
    }

    @Test
    public void changeTrialStatus() throws Exception {

        assertThat(userRepository.count(), is(0L));
        User ToCreate = new User("07515757537", null, null);
        userRepository.save(ToCreate);

        User retrieve = userRepository.findAll().iterator().next();
        assertNotNull(retrieve.getId());
        assertThat(retrieve.getPhoneNumber(), is("07515757537"));
        assertFalse(retrieve.isHasUsedFreeTrial());
        retrieve.setHasUsedFreeTrial(true);
        userRepository.save(retrieve);
    }

    @Test
    public void shouldSaveAndFetchName() {

        User entity = new User("232331", null, null);
        assertNotNull(entity.getUid());
        entity.setDisplayName("john");
        assertTrue(entity.hasName());
        assertThat(entity.getName(), is("john"));
        userRepository.save(entity);

        User fromDb = userRepository.findAll().iterator().next();
        assertNotNull(fromDb.getUid());
        assertThat(entity.getName(), is("john"));
    }

    @Test
    public void shouldSetDisplayName() {
        User entity = new User("2435", "", null);
        assertNotNull(entity.getUid());
        assertFalse(entity.hasName());
        assertThat(entity.getName(""), is("035"));
        assertThat(entity.getName("name"), is("name (035)"));
        assertThat(entity.getPhoneNumber(), is("2435"));
        userRepository.save(entity);
        User userDb = userRepository.findOneByUid(entity.getUid());
        assertNotNull(userDb.getUid());
        assertThat(entity.getPhoneNumber(), is("2435"));
    }

    @Test
    public void checkOriginalTrialStatus() throws Exception {
        assertThat(userRepository.count(), is(0L));

        User dbz = new User("07515757537", null, null);
        userRepository.save(dbz);

        User check = userRepository.findAll().iterator().next();
        assertNotNull(check.getId());
        assertThat(check.getPhoneNumber(), is("07515757537"));
        assertFalse(check.isHasUsedFreeTrial());
    }

    @Test
    public void shouldSaveAndRetrieveUserData() throws Exception {
        assertThat(userRepository.count(), is(0L));

        User userToCreate = new User("12345", null, null);
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

        User firstUserToCreate = new User("12345", null, null);
        firstUserToCreate = userRepository.save(firstUserToCreate);

        User userFromDb = userRepository.findAll().iterator().next();
        assertThat(userFromDb.getId(), is(firstUserToCreate.getId()));
        assertThat(userFromDb.getPhoneNumber(), is("12345"));


        User secondUserToCreate = new User("12345", null, null);

        userRepository.save(secondUserToCreate);
        fail("Saving a user with the phone number of an already existing user should throw an exception");
    }

    @Test
    public void shouldSaveAndFindByPhoneNumber() throws Exception {
        User userToCreate = new User("54321", null, null);
        assertNull(userToCreate.getId());
        assertNotNull(userToCreate.getUid());
        User savedUser = userRepository.save(userToCreate);
        Assert.assertNotEquals(Long.valueOf(0), savedUser.getId());
        User foundUser = userRepository.findByPhoneNumberAndPhoneNumberNotNull("54321");
        assertEquals(savedUser.getPhoneNumber(), foundUser.getPhoneNumber());
    }

    @Test
    public void shouldNotFindPhoneNumber() throws Exception {
        User dbUser = userRepository.findByPhoneNumberAndPhoneNumberNotNull("99999999999");
        assertNull(dbUser);
    }

    @Test
    public void shouldNotExist() {
        assertEquals(false, userRepository.existsByPhoneNumber("99999999999"));
    }

    @Test
    public void shouldExist() {
        userRepository.save(new User("4444444", null, null));
        assertEquals(true, userRepository.existsByPhoneNumber("4444444"));
    }

    @Test
    public void shouldReturnUserThatRSVPYes() {
        User u1 = userRepository.save(new User("0821234560", null, null));
        User u2 = userRepository.save(new User("0821234561", null, null));
        Group group = groupRepository.save(new Group("rsvp yes",u1));
        group.addMember(u2, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        group = groupRepository.save(group);
        Event event = eventRepository.save(new MeetingBuilder().setName("rsvp event").setStartDateTime(Instant.now()).setUser(u1).setParent(group).setEventLocation("someLocation").setIncludeSubGroups(true).createMeeting());
        EventLog eventLog = eventLogRepository.save(new EventLog(u1, event, EventLogType.RSVP, EventRSVPResponse.YES));
        List<User> list = userRepository.findUsersThatRSVPYesForEvent(event);
        log.info("list.size..." + list.size() + "...first user..." + list.get(0).getPhoneNumber());
        assertEquals(u1.getPhoneNumber(),list.get(0).getPhoneNumber());
    }

    @Test
    public void shouldReturnUserThatRSVPNo() {
        User u1 = userRepository.save(new User("0821234570", null, null));
        User u2 = userRepository.save(new User("0821234571", null, null));

        Group group = groupRepository.save(new Group("rsvp yes",u1));
        group.addMember(u2, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        group = groupRepository.save(group);

        Event event = eventRepository.save(new MeetingBuilder().setName("rsvp event").setStartDateTime(Instant.now()).setUser(u1).setParent(group).setEventLocation("someLocation").setIncludeSubGroups(true).createMeeting());
		eventLogRepository.save(new EventLog(u1, event, EventLogType.RSVP, EventRSVPResponse.YES));
        eventLogRepository.save(new EventLog(u2, event, EventLogType.RSVP, EventRSVPResponse.NO));

        List<User> list = userRepository.findUsersThatRSVPNoForEvent(event);
        assertEquals(u2.getPhoneNumber(),list.get(0).getPhoneNumber());
    }

    @Test
    @Rollback
    public void shouldSaveAddedRole() {
        User user = userRepository.save(new User(number, null, null));
        Role role = roleRepository.save(new Role("TEST_ROLE", null));
        user.addStandardRole(role);
        userRepository.save(user);
        User userFromDb = userRepository.findByPhoneNumberAndPhoneNumberNotNull(number);
        assertNotNull(userFromDb);
        assertEquals(userFromDb.getId(), user.getId());
        assertTrue(userFromDb.getStandardRoles().contains(role));
    }

    @Test
    @Rollback
    public void shouldRemoveRole() {
        User user = userRepository.save(new User(number, null, null));
        Role role = roleRepository.save(new Role("TEST_ROLE", null));
        user.addStandardRole(role);
        user = userRepository.save(user);
        assertTrue(user.getStandardRoles().contains(role));
        user.removeStandardRole(role);
        userRepository.save(user);
        User userfromDb = userRepository.findByPhoneNumberAndPhoneNumberNotNull(number);
        assertNotNull(userfromDb);
        assertEquals(userfromDb.getId(), user.getId());
        assertFalse(userfromDb.getStandardRoles().contains(role));
    }

    @Test
    @Rollback
    public void shouldFindUsersByStringsAndGroup() {
        assertThat(userRepository.count(), is(0L));

        String phoneBase = "080111000";

        User user1 = userRepository.save(new User(phoneBase + "1", "tester1", null));
        User user2 = userRepository.save(new User(phoneBase + "2", "anonymous", null));
        User user3 = userRepository.save(new User(phoneBase + "3", "tester2", null));
        User user4 = userRepository.save(new User("0701110001", "tester3", null));
        User user5 = userRepository.save(new User("0701110002", "no name", null));

        Group testGroup = groupRepository.save(new Group("test group", user1));
        testGroup.addMember(user1, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        testGroup.addMember(user2, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
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

        User testUser = userRepository.save(new User(phoneBase +" 1", "tester1", null));
        User user2 = userRepository.save(new User(phoneBase + "2", "anonymous", null));
        User user3 = userRepository.save(new User(phoneBase + "3", "tester2", null));

        Group testGroup = groupRepository.save(new Group("tg1", testUser));
        testGroup.addMember(testUser, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        testGroup.addMember(user2, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        testGroup.addMember(user3, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
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
