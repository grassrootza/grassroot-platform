package za.org.grassroot.webapp.controller.ussd;

import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.annotation.Rollback;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.domain.geo.TaskLocation;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupPermissionTemplate;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.MeetingBuilder;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.geo.GeographicSearchType;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class UssdAdvancedHomeServiceTest extends UssdUnitTest {

	private static final String phoneForTests = "27810001111";
	private static final String testUserName = "Test User";

	private static final double testLat = -11.00;
	private static final double testLong = 12.00;
	private static final Integer testRadius = 5000;

	private static final User testUser = new User(phoneForTests, testUserName, null);
	protected final static String testGroupName = "test_group";

	protected final static Group testGroup = new Group(testGroupName, GroupPermissionTemplate.DEFAULT_GROUP, testUser);

	private UssdAdvancedHomeService ussdAdvancedHomeService;

	@Before
	public void setUp() {
		this.ussdAdvancedHomeService = new UssdAdvancedHomeServiceImpl(ussdLocationServicesBrokerMock, null, geoLocationBrokerMock, eventBrokerMock, userManagementServiceMock, ussdSupport);
	}

	@Test
	@Rollback
	public void advancedUssdWelcomeMenuShouldWork() throws Exception {
		when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);

		Request request = this.ussdAdvancedHomeService.processMoreOptions(phoneForTests);
		Assertions.assertThat(request).isNotNull();
	}

	@Test
	public void getPublicMeetingsNearUserShouldWork() throws Exception {
		GeoLocation testLocation = new GeoLocation(testLat, testLong);

		when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);
		when(geoLocationBrokerMock.fetchBestGuessUserLocation(testUser.getUid())).thenReturn(testLocation);

		List<ObjectLocation> actualObjectLocations = new ArrayList<>();

		Group testGroup = new Group("test Group", GroupPermissionTemplate.DEFAULT_GROUP, testUser);

		Meeting testMeeting = new MeetingBuilder().setName("test meeting")
				.setStartDateTime(Instant.now().plus(1, ChronoUnit.DAYS))
				.setUser(testUser).setParent(testGroup).setEventLocation("place").createMeeting();
		TaskLocation meetingLocation = new TaskLocation(testMeeting, testLocation, 0,
				EventType.MEETING, LocationSource.LOGGED_APPROX);

		ObjectLocation objectLocation = new ObjectLocation(testMeeting, meetingLocation);
		actualObjectLocations.add(objectLocation);

		when(geoLocationBrokerMock.fetchMeetingLocationsNearUser(testUser, testLocation, testRadius, GeographicSearchType.PUBLIC, null))
				.thenReturn(actualObjectLocations);

		this.ussdAdvancedHomeService.processGetPublicMeetingNearUser(phoneForTests, null, false);

		verify(geoLocationBrokerMock, times(1)).fetchBestGuessUserLocation(testUser.getUid());
		verify(geoLocationBrokerMock, times(1)).fetchMeetingLocationsNearUser(testUser, testLocation, testRadius, GeographicSearchType.PUBLIC, null);
	}

	// todo: VJERAN: couldn't redesign this test because I cannot see how older version worked without "meetingUid" param?
/*
	@Test
	public void meetingDetailsShouldWork() throws Exception {
		Meeting testMeeting = new MeetingBuilder().setName("test meeting")
				.setStartDateTime(Instant.now().plus(1, ChronoUnit.DAYS))
				.setUser(testUser).setParent(testGroup).setEventLocation("place").createMeeting();

		when(userManagementServiceMock.findByInputNumber(testUser.getUid())).thenReturn(testUser);
		when(eventBrokerMock.loadMeeting(testMeeting.getUid())).thenReturn(testMeeting);

		Request request = this.ussdAdvancedHomeService.processMeetingDetails(phoneForTests, null);
		Assert.assertNotNull(request);
	}
*/

	@Test
	public void trackUserShouldWork() throws Exception {
		when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);
		when(ussdLocationServicesBrokerMock.addUssdLocationLookupAllowed(testUser.getUid(), UserInterfaceType.USSD)).thenReturn(true);

		Request request = this.ussdAdvancedHomeService.processTrackMe(phoneForTests);

		Assert.assertNotNull(request);
		verify(ussdLocationServicesBrokerMock, times(1)).addUssdLocationLookupAllowed(testUser.getUid(), UserInterfaceType.USSD);
	}
}
