package za.org.grassroot.webapp.controller.rest.location;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
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

import java.security.InvalidParameterException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

public class AroundMeRestControllerTest extends RestAbstractUnitTest {

    @Autowired
    private static final Logger logger = LoggerFactory.getLogger(AroundMeRestControllerTest.class);

    @InjectMocks
    private AroundMeRestController aroundMeRestControllerMock;

    @Mock
    private ObjectLocationBroker objectLocationBrokerMock;

    @Mock
    private UserRepository userRepositoryInMem;

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

        User testUser = new User(phoneForTests,testUserName, null);
        String testSearchTerm = "searchTerm";

        Meeting testMeeting = new MeetingBuilder().setName("test meeting")
                .setStartDateTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .setUser(testUser).setParent(testGroup).setEventLocation("place").createMeeting();

        MeetingLocation meetingLocation = new MeetingLocation(testMeeting,testLocation,0, EventType.MEETING, LocationSource.LOGGED_APPROX);

        ObjectLocation objectLocation = new ObjectLocation(testMeeting,meetingLocation);

        List<ObjectLocation> objectLocationList = new ArrayList<>();
        objectLocationList.add(objectLocation);

        when(userManagementServiceMock.load(testUser.getUid())).thenReturn(testUser);

        when(objectLocationBrokerMock
                .fetchMeetingLocationsNearUser(testUser,null,testRadiusMetres, GeographicSearchType.PUBLIC,testSearchTerm))
                .thenThrow(new InvalidParameterException("Invalid Location parameter"));

        when(objectLocationBrokerMock
                .fetchMeetingLocationsNearUser(testUser,testLocation,-5, GeographicSearchType.PUBLIC,testSearchTerm))
                .thenThrow(new InvalidParameterException("Invalid Radius parameter"));

        when(objectLocationBrokerMock
                .fetchMeetingLocationsNearUser(testUser,testLocation,testRadiusMetres, GeographicSearchType.PUBLIC,testSearchTerm))
                .thenReturn(objectLocationList);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders
                .get(path + "/all/{userUid}",testUser.getUid())
                .param("longitude",""+testLocation.getLongitude())
                .param("latitude",""+testLocation.getLatitude())
                .param("radiusMetres",""+ testRadiusMetres)
                .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        Assert.assertNotNull(result);

        logger.info("Testing All Entities Results = {}",result.getResponse().getStatus());

        // just reaffirms 'when' set up above
        Assert.assertNotNull(objectLocationBrokerMock.fetchMeetingLocationsNearUser(testUser,testLocation,testRadiusMetres, GeographicSearchType.PUBLIC,testSearchTerm));

        verify(userManagementServiceMock, times(1)).load(testUser.getUid());
        verify(objectLocationBrokerMock, times(1)).fetchMeetingLocationsNearUser(testUser, testLocation,
                testRadiusMetres, GeographicSearchType.PUBLIC, testSearchTerm);
    }

    @Test
    public void getPublicGroupsNearUserShouldWork() throws Exception{
        GeoLocation testLocation = new GeoLocation(testLat,testLong);

        User testUser = new User(phoneForTests,testUserName, null);

        Group testGroup = new Group("test Group", new User("121212121", null, null));
        GroupLocation groupLocation = new GroupLocation(testGroup,LocalDate.now(),testLocation,0,LocationSource.LOGGED_APPROX);

        ObjectLocation objectLocation = new ObjectLocation(testGroup,groupLocation);
        List<ObjectLocation> objectLocations = new ArrayList<>();
        objectLocations.add(objectLocation);

        when(objectLocationBrokerMock
                .fetchGroupsNearby(testUser.getUid(),null,testRadiusMetres,testFilterTerm,GeographicSearchType.PUBLIC))
                .thenThrow(new InvalidParameterException("Invalid location parameter"));

        when(objectLocationBrokerMock
                .fetchGroupsNearby(testUser.getUid(),testLocation,-5,testFilterTerm,GeographicSearchType.PUBLIC))
                .thenThrow(new InvalidParameterException("Invalid radius parameter"));

        when(objectLocationBrokerMock
                .fetchGroupsNearby(testUser.getUid(),testLocation,testRadiusMetres,testFilterTerm,GeographicSearchType.PUBLIC))
                .thenReturn(objectLocations);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders
                .get(path + "/all/groups/{userUid}",testUser.getUid())
                .param("longitude",""+testLocation.getLongitude())
                .param("latitude",""+testLocation.getLatitude())
                .param("radiusMetres",""+ testRadiusMetres)
                .param("filterTerm",testFilterTerm)
                .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        Assert.assertNotNull(result);
        logger.info("Testing Groups Near User Results = {}",result.getResponse().getStatus());

        verify(objectLocationBrokerMock,times(1))
                .fetchGroupsNearby(testUser.getUid(),testLocation,testRadiusMetres,testFilterTerm,GeographicSearchType.PUBLIC);
        Assert.assertNotNull(objectLocationBrokerMock.fetchGroupsNearby(uidParameter,testLocation,testRadiusMetres,testFilterTerm,GeographicSearchType.PUBLIC));
    }

    @Test
    public void getAlertsNearUserShouldWork() throws Exception{
        GeoLocation testLocation = new GeoLocation(testLat,testLong);
        String testCreatedByMe = "createdByMe";
        User testUser = new User(phoneForTests,testUserName, null);
        Group testGroup = new Group("test group", testUser);

        LiveWireAlert.Builder builder = LiveWireAlert.newBuilder();
        builder.creatingUser(testUser)
                .description("Test alert")
                .contactUser(testUser)
                .type(LiveWireAlertType.INSTANT)
                .group(testGroup)
                .destType(LiveWireAlertDestType.PUBLIC_LIST);

        LiveWireAlert liveWireAlert = builder.build();

        List<LiveWireAlert> liveWireAlerts = new ArrayList<>();
        liveWireAlerts.add(liveWireAlert);

        when(liveWireAlertBrokerMock
                .fetchAlertsNearUser(testUser.getUid(),null,
                        testRadiusMetres,GeographicSearchType.PUBLIC)).thenThrow(new IllegalArgumentException("Invalid location parameter"));

        when(liveWireAlertBrokerMock
                .fetchAlertsNearUser(testUser.getUid(),null,
                        -5,GeographicSearchType.PUBLIC)).thenThrow(new IllegalArgumentException("Invalid radius parameter"));

        when(liveWireAlertBrokerMock
                .fetchAlertsNearUser(testUser.getUid(),testLocation,
                        testRadiusMetres,GeographicSearchType.PUBLIC)).thenReturn(liveWireAlerts);




        MvcResult result = mockMvc.perform(MockMvcRequestBuilders
                .get(path + "/all/alerts/{userUid}",testUser.getUid())
                .param("longitude",""+testLocation.getLongitude())
                .param("latitude",""+testLocation.getLatitude())
                .param("radiusMetres",""+ testRadiusMetres)
                .param("createdByMe",testCreatedByMe)
                .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        Assert.assertNotNull(result);
        logger.info("Testing All Alerts Results = {}",result.getResponse().getStatus());
        verify(liveWireAlertBrokerMock,times(1))
                .fetchAlertsNearUser(testUser.getUid(),testLocation, testRadiusMetres,GeographicSearchType.PUBLIC);
        Assert.assertNotNull(liveWireAlertBrokerMock.fetchAlertsNearUser(uidParameter,testLocation,
                testRadiusMetres,GeographicSearchType.PUBLIC));
    }

}
