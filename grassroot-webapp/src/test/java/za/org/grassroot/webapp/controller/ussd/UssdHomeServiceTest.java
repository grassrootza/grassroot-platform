package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupPermissionTemplate;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * Created by luke on 2015/11/20.
 */
public class UssdHomeServiceTest extends UssdUnitTest {

    private static final String phoneForTests = "27810001111";
    private static final String testUserName = "Test User";
    private static final String testGroupName = "Test Group";

    private User testUser;
    private UssdHomeService ussdHomeService;
	private UssdLiveWireService ussdLiveWireServiceMock;

    @Before
    public void setUp() {
        testUser = new User(phoneForTests, testUserName, null);
        testUser.setLanguageCode("en");

		this.ussdLiveWireServiceMock = mock(UssdLiveWireService.class);
		this.ussdHomeService = new UssdHomeServiceImpl(ussdSupport, this.ussdLiveWireServiceMock, null, null, null, null, null,
                locationInfoBrokerMock, userManagementServiceMock, campaignBrokerMock, null, userLoggerMock, cacheUtilManagerMock, userResponseBrokerMock, groupQueryBrokerMock, accountFeaturesBrokerMock, groupBrokerMock);
    }

    @Test
    public void testQuestion() throws Exception {
        this.ussdHomeService.processTestQuestion();
    }

    @Test
    public void welcomeMenuShouldWork() throws Exception {
        testUser.setHasInitiatedSession(false);

        when(userManagementServiceMock.loadOrCreateUser(phoneForTests, UserInterfaceType.USSD)).thenReturn(testUser);

        this.ussdHomeService.processStartMenu(phoneForTests, null);

        verify(userManagementServiceMock, times(1)).loadOrCreateUser(phoneForTests, UserInterfaceType.USSD);
    }

    @Test
    public void welcomeMenuAfterChoosingLanguageShouldWork() throws Exception {
        testUser.setHasInitiatedSession(true);
        testUser.setLanguageCode("zu");
        when(userManagementServiceMock.loadOrCreateUser(phoneForTests, UserInterfaceType.USSD)).thenReturn(testUser);

        this.ussdHomeService.processStartMenu(phoneForTests, null);
    }

    @Test
    public void welcomeMenuLocaleShouldWorkWithNoName() throws Exception {

        testUser.setDisplayName("");
        testUser.setHasInitiatedSession(false);
        when(userManagementServiceMock.loadOrCreateUser(phoneForTests, UserInterfaceType.USSD)).thenReturn(testUser);

        this.ussdHomeService.processStartMenu(phoneForTests, null);

        testUser.setLanguageCode("ts");

        this.ussdHomeService.processStartMenu(phoneForTests, null);

        // no longer done here -- check via language menu
        // verify(userManagementServiceMock, times(1)).updateUserLanguage(testUser.getUid(), new Locale("ts"));
        testUser.setDisplayName(testUserName);
    }

    @Test
    public void forcedStartMenuShouldWork() throws Exception {
        testUser.setHasInitiatedSession(true);
        // switch the next thing to cache manager
        // testUser.setLastUssdMenu("/ussd/mtg/start"); // irrelevant if this is well formed or not, just testing if it asks

        when(userManagementServiceMock.loadOrCreateUser(phoneForTests, UserInterfaceType.USSD)).thenReturn(testUser);

        this.ussdHomeService.processStartMenu(phoneForTests, null);

        this.ussdHomeService.processForceStartMenu(phoneForTests, null);
    }

    @Test
    public void groupJoinTokenShouldWorkInAllLanguages() throws Exception {
        testUser.setDisplayName("");
        testUser.setHasInitiatedSession(false);

        final Group testGroup = new Group(testGroupName, GroupPermissionTemplate.DEFAULT_GROUP, new User("27601110000", null, null));
        testGroup.setGroupTokenCode("111");
        testGroup.setTokenExpiryDateTime(Instant.now().plus(7, ChronoUnit.DAYS));

        when(userManagementServiceMock.loadOrCreateUser(phoneForTests, UserInterfaceType.USSD)).thenReturn(testUser);
        when(groupQueryBrokerMock.findGroupFromJoinCode("111")).thenReturn(Optional.empty());

        this.ussdHomeService.processStartMenu(phoneForTests, "*134*1994*111#");

        verify(groupQueryBrokerMock, times(1)).findGroupFromJoinCode("111");
    }

    @Test
    public void shouldAssembleLiveWire() throws Exception {
        when(userManagementServiceMock.loadOrCreateUser(phoneForTests, UserInterfaceType.USSD)).thenReturn(testUser);
        when(ussdLiveWireServiceMock.assembleLiveWireOpening(testUser, 0)).thenReturn(new USSDMenu("LiveWire Menu"));

        this.ussdHomeService.processStartMenu(phoneForTests, "*134*1994*411#");

        verify(userManagementServiceMock, times(1)).loadOrCreateUser(phoneForTests, UserInterfaceType.USSD);
        verify(ussdLiveWireServiceMock, times(1)).assembleLiveWireOpening(testUser, 0);
    }

    /*
    Make sure that exit page works properly
     */
    @Test
    public void errorPagesShouldBeWellFormed() throws Exception {
        when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);

        this.ussdHomeService.processNotBuilt(phoneForTests);

        verify(userManagementServiceMock, times(1)).findByInputNumber(phoneForTests);
        verifyNoMoreInteractions(userManagementServiceMock);

    }
}
