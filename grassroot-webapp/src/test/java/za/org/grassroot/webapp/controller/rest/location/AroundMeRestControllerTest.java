package za.org.grassroot.webapp.controller.rest.location;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.GroupLocation;
import za.org.grassroot.core.domain.geo.MeetingLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.MeetingBuilder;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.LiveWireAlertDestType;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.geo.GeographicSearchType;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.webapp.controller.rest.RestAbstractUnitTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;

public class AroundMeRestControllerTest extends RestAbstractUnitTest {

    @InjectMocks
    private AroundMeRestController aroundMeRestControllerMock;

    @Mock
    private ObjectLocationBroker objectLocationBrokerMock;

    @Mock
    private UserRepository userRepositoryMock;

    @Mock
    private LiveWireAlertBroker liveWireAlertBrokerMock;

    private String path = "/api/location";
    private String uidParameter = "userUid";
    private String testFilterTerm = "filterTerm";

    private static final String phoneForTests = "27810001111";
    private static final String testUserName = "Test User";

    private int testRadiusMetres = 5;


    private static final double testLat = -11.00;
    private static final double testLong = 11.00;

    @Before
    public void setUp(){
        mockMvc = MockMvcBuilders.standaloneSetup(aroundMeRestControllerMock).build();
    }

    @Test
    public void fetchAllEntitiesNearUserShouldWork() throws Exception{
        GeoLocation testLocation = new GeoLocation(testLat,testLong);
        User user = userRepositoryMock.findOneByUid(uidParameter);
        User testUser = new User(phoneForTests,testUserName);
        String testSearchTerm = "searchTerm";

        //when(!testLocation.isValid()).thenThrow(new InvalidParameterException("Invalid Geolocation object"));
        //Assert.assertNotNull(objectLocationBrokerMock.fetchGroupsNearby(user.getUid(),testLocation,testRadiusMetres,testFilterTerm,GeographicSearchType.BOTH));

        Meeting testMeeting = new MeetingBuilder().setName("test meeting")
                .setStartDateTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .setUser(testUser).setParent(testGroup).setEventLocation("place").createMeeting();

        MeetingLocation meetingLocation = new MeetingLocation(testMeeting,testLocation,0, EventType.MEETING, LocationSource.LOGGED_APPROX);

        ObjectLocation objectLocation = new ObjectLocation(testMeeting,meetingLocation);

        List<ObjectLocation> objectLocationList = new ArrayList<>();
        objectLocationList.add(objectLocation);

        when(objectLocationBrokerMock
                .fetchMeetingLocationsNearUser(user,testLocation,testRadiusMetres, GeographicSearchType.PUBLIC,testSearchTerm))
                .thenReturn(objectLocationList);

        Assert.assertNotNull(objectLocationBrokerMock.fetchMeetingLocationsNearUser(user,testLocation,testRadiusMetres, GeographicSearchType.PUBLIC,testSearchTerm));
    }

    @Test
    public void getPublicGroupsNearUserShouldWork() throws Exception{
        GeoLocation testLocation = new GeoLocation(testLat,testLong);

        Group testGroup = new Group("test Group", new User("121212121"));
        GroupLocation groupLocation = new GroupLocation(testGroup,LocalDate.now(),testLocation,0,LocationSource.LOGGED_APPROX);

        ObjectLocation objectLocation = new ObjectLocation(testGroup,groupLocation);
        List<ObjectLocation> objectLocations = new ArrayList<>();
        objectLocations.add(objectLocation);

        when(objectLocationBrokerMock
                .fetchGroupsNearby(uidParameter,testLocation,testRadiusMetres,testFilterTerm,GeographicSearchType.PUBLIC))
                .thenReturn(objectLocations);

        Assert.assertNotNull(objectLocationBrokerMock.fetchGroupsNearby(uidParameter,testLocation,testRadiusMetres,testFilterTerm,GeographicSearchType.PUBLIC));
    }

    @Test
    public void getAlertsNearUserShouldWork() throws Exception{
        GeoLocation testLocation = new GeoLocation(testLat,testLong);
        String testCreatedByMe = "createdByMe";
        User testUser = new User(phoneForTests,testUserName);

        LiveWireAlert.Builder builder = LiveWireAlert.newBuilder();
        builder.creatingUser(testUser)
                .description("Test alert")
                .type(LiveWireAlertType.MEETING)
                .destType(LiveWireAlertDestType.PUBLIC_LIST);

        LiveWireAlert liveWireAlert = builder.build();

        List<LiveWireAlert> liveWireAlerts = new ArrayList<>();
        liveWireAlerts.add(liveWireAlert);

        when(liveWireAlertBrokerMock.fetchAlertsNearUser(uidParameter,testLocation,testCreatedByMe,testRadiusMetres,GeographicSearchType.PUBLIC)).thenReturn(liveWireAlerts);

        Assert.assertNotNull(liveWireAlertBrokerMock.fetchAlertsNearUser(uidParameter,testLocation,testCreatedByMe,testRadiusMetres,GeographicSearchType.PUBLIC));
    }

}
