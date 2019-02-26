package za.org.grassroot.webapp.controller.rest;

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
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.GroupLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.domain.geo.TaskLocation;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupPermissionTemplate;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.MeetingBuilder;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.LiveWireAlertDestType;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.geo.GeographicSearchType;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.webapp.controller.rest.location.AroundMeRestController;

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
    private GeoLocationBroker geoLocationBrokerMock;

    @Mock
    private LiveWireAlertBroker liveWireAlertBrokerMock;

    @Mock
    private JwtService jwtServiceMock;

    private String path = "/v2/api/location";
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

        TaskLocation meetingLocation = new TaskLocation(testMeeting,testLocation,0, EventType.MEETING, LocationSource.LOGGED_APPROX);

        ObjectLocation objectLocation = new ObjectLocation(testMeeting,meetingLocation);

        List<ObjectLocation> objectLocationList = new ArrayList<>();
        objectLocationList.add(objectLocation);

        when(jwtServiceMock.getUserIdFromJwtToken(nullable(String.class))).thenReturn(testUser.getUid());

        when(userManagementServiceMock.load(testUser.getUid())).thenReturn(testUser);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders
                .get(path + "/all")
                .header("Authorization", "Bearer somethingorother")
                .param("longitude",""+testLocation.getLongitude())
                .param("latitude",""+testLocation.getLatitude())
                .param("radiusMetres",""+ testRadiusMetres)
                .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        Assert.assertNotNull(result);

        logger.info("Testing All Entities Results = {}",result.getResponse().getStatus());

        // just reaffirms 'when' set up above
        Assert.assertNotNull(geoLocationBrokerMock.fetchMeetingLocationsNearUser(testUser,testLocation,testRadiusMetres, GeographicSearchType.PUBLIC,testSearchTerm));

        verify(userManagementServiceMock, times(1)).load(testUser.getUid());
        verify(geoLocationBrokerMock, times(1)).fetchMeetingLocationsNearUser(testUser, testLocation,
                testRadiusMetres, GeographicSearchType.PUBLIC, testSearchTerm);
    }

    @Test
    public void getPublicGroupsNearUserShouldWork() throws Exception {
        GeoLocation testLocation = new GeoLocation(testLat,testLong);

        User testUser = new User(phoneForTests,testUserName, null);

        Group testGroup = new Group("test Group", GroupPermissionTemplate.DEFAULT_GROUP, new User("121212121", null, null));
        GroupLocation groupLocation = new GroupLocation(testGroup,LocalDate.now(),testLocation,0,LocationSource.LOGGED_APPROX);

        ObjectLocation objectLocation = new ObjectLocation(testGroup,groupLocation);
        List<ObjectLocation> objectLocations = new ArrayList<>();
        objectLocations.add(objectLocation);

        when(jwtServiceMock.getUserIdFromJwtToken(nullable(String.class))).thenReturn(testUser.getUid());

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders
                .get(path + "/all/groups")
                .header("Authorization", "Bearer somethingorother")
                .param("longitude",""+testLocation.getLongitude())
                .param("latitude",""+testLocation.getLatitude())
                .param("radiusMetres",""+ testRadiusMetres)
                .param("filterTerm",testFilterTerm)
                .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        Assert.assertNotNull(result);
        logger.info("Testing Groups Near User Results = {}",result.getResponse().getStatus());

        verify(geoLocationBrokerMock,times(1))
                .fetchGroupsNearby(testUser.getUid(),testLocation,testRadiusMetres,testFilterTerm,GeographicSearchType.PUBLIC);
        Assert.assertNotNull(geoLocationBrokerMock.fetchGroupsNearby(uidParameter,testLocation,testRadiusMetres,testFilterTerm,GeographicSearchType.PUBLIC));
    }

    @Test
    public void getAlertsNearUserShouldWork() throws Exception{
        GeoLocation testLocation = new GeoLocation(testLat,testLong);
        String testCreatedByMe = "createdByMe";
        User testUser = new User(phoneForTests,testUserName, null);
        Group testGroup = new Group("test group", GroupPermissionTemplate.DEFAULT_GROUP, testUser);

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

        when(jwtServiceMock.getUserIdFromJwtToken(nullable(String.class))).thenReturn(testUser.getUid());

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders
                .get(path + "/all/alerts")
                .header("Authorization", "Bearer somethingorother")
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
