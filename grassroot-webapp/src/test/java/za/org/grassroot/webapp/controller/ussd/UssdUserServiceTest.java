package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.geo.AddressBroker;

import static org.mockito.Mockito.*;


public class UssdUserServiceTest extends UssdUnitTest {

	private static final String testUserPhone = "27801115555";

	private User testUser;

	private UssdHomeService ussdHomeService;
	private UssdUserService ussdUserService;

	@Before
	public void setUp() {
		testUser = new User(testUserPhone, null, null);

		this.ussdUserService = new UssdUserServiceImpl(ussdSupport, userManagementServiceMock, userLoggerMock, locationInfoBrokerMock, mock(AddressBroker.class));
		this.ussdHomeService = new UssdHomeServiceImpl(ussdSupport, null, null, null, null, null, null, locationInfoBrokerMock, userManagementServiceMock, campaignBrokerMock, null, userLoggerMock, cacheUtilManagerMock, userResponseBrokerMock, groupQueryBrokerMock, accountFeaturesBrokerMock, groupBrokerMock);
	}

	/*
	User rename should work properly
	 */
	@Test
	public void userRenamePromptShouldWork() throws Exception {
		testUser.setHasInitiatedSession(true);
		testUser.setDisplayName("");

		when(userManagementServiceMock.loadOrCreateUser(testUserPhone, UserInterfaceType.USSD)).thenReturn(testUser);
		when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
		when(userManagementServiceMock.needsToSetName(testUser, false)).thenReturn(true);

		this.ussdHomeService.processStartMenu(testUserPhone, null);

		testUser.setDisplayName("now it is set"); // necessary else when/then doesn't work within controller

		this.ussdUserService.processRenameAndStart(testUserPhone, "now it is set");

		verify(userManagementServiceMock, times(1)).loadOrCreateUser(testUserPhone, UserInterfaceType.USSD);
		verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
		verify(userManagementServiceMock, times(1)).updateDisplayName(testUser.getUid(), testUser.getUid(), "now it is set");
	}

	@Test
	public void startMenuShouldWork() throws Exception {
		when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);

		this.ussdUserService.processUserProfile(testUserPhone);

		verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
		verifyNoMoreInteractions(userManagementServiceMock);
		verifyZeroInteractions(groupBrokerMock);
		verifyZeroInteractions(eventBrokerMock);
	}


	@Test
	public void renameSelfPromptShouldWork() throws Exception {
		when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
		User namedUser = new User("27801115550", "named", null);
		when(userManagementServiceMock.findByInputNumber(namedUser.getPhoneNumber())).thenReturn(namedUser);

		this.ussdUserService.processUserDisplayName(testUserPhone);
		this.ussdUserService.processUserDisplayName(namedUser.getPhoneNumber());

		verify(userManagementServiceMock, times(2)).findByInputNumber(nullable(String.class));
		verifyNoMoreInteractions(groupBrokerMock);
		verifyNoMoreInteractions(eventBrokerMock);
	}

	@Test
	public void renameSelfDoneScreenShouldWork() throws Exception {
		User namedUser = new User("278011115550", "named", null);
		when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
		when(userManagementServiceMock.findByInputNumber(namedUser.getPhoneNumber())).thenReturn(namedUser);

		this.ussdUserService.processUserChangeName(testUserPhone, "naming");
		this.ussdUserService.processUserChangeName(namedUser.getPhoneNumber(), "new name");

		verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
		verify(userManagementServiceMock, times(1)).findByInputNumber(namedUser.getPhoneNumber());
		verify(userManagementServiceMock, times(1)).updateDisplayName(testUser.getUid(), testUser.getUid(), "naming");
		verify(userManagementServiceMock, times(1)).updateDisplayName(namedUser.getUid(), namedUser.getUid(), "new name");
		verifyNoMoreInteractions(userManagementServiceMock);
		verifyZeroInteractions(groupBrokerMock);
		verifyZeroInteractions(eventBrokerMock);
	}
}
