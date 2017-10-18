package za.org.grassroot.webapp.controller.ussd;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.integration.location.UssdLocationServicesBroker;
import za.org.grassroot.services.geo.GeographicSearchType;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.services.task.EventBroker;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(MockitoJUnitRunner.class)
public class USSDAdvancedHomeControllerTest extends USSDAbstractUnitTest{

    private static final String phoneForTests = "27810001111";
    private static final String testUserName = "Test User";

    private static final String openingMenu = "/ussd/more";
    private static final String phoneParameter = "msisdn";
    private static final String meetingParameter = "meetingUid";

    private static final double testLat = -11.00;
    private static final double testLong = 12.00;
    private static final Integer testRadius = 5;

    private User testUser = new User(phoneForTests, testUserName);

    @InjectMocks
    private USSDHomeController ussdHomeController;

    @InjectMocks
    private USSDAdvancedHomeController ussdAdvancedHomeController;

    @Mock
    private UssdLocationServicesBroker ussdLocationServicesBroker;

    @Mock
    private ObjectLocationBroker objectLocationBroker;

    @Mock
    private EventBroker eventBroker;

    @Before
    public void setUp(){
        mockMvc = MockMvcBuilders.standaloneSetup(ussdHomeController,ussdAdvancedHomeController).build();
        wireUpMessageSourceAndGroupUtil(ussdAdvancedHomeController);
    }

    @Test
    @Rollback
    public void advancedUssdWelcomeMenuShouldWork() throws Exception{
        User testUser = new User(phoneForTests);

        when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);

        mockMvc.perform(get(openingMenu + "/start").param(phoneParameter, phoneForTests)).andExpect(status().isOk());
    }

    @Test
    public void getPublicMeetingsNearUserShouldWork() throws Exception{
        User testUser = new User(phoneForTests);
        GeoLocation testLocation = new GeoLocation(testLat,testLong);

        when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);
        when(objectLocationBroker.fetchBestGuessUserLocation(testUser.getUid())).thenReturn(testLocation);

        List<ObjectLocation> objectLocations = objectLocationBroker.fetchMeetingLocationsNearUser(testUser,testLocation,testRadius, GeographicSearchType.PUBLIC,null);

        Assert.assertNotNull(objectLocations);
        mockMvc.perform(get(openingMenu + "/public/mtgs").param(phoneParameter, phoneForTests)).andExpect(status().is(200));
    }

    @Test
    public void meetingDetailsShouldWork() throws Exception{
        User testUser = new User(phoneForTests);
        Meeting testMeeting = eventBroker.loadMeeting(meetingParameter);

        when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);
        when(eventBroker.loadMeeting(meetingParameter)).thenReturn(testMeeting);

    }

    @Test
    public void trackUserShouldWork() throws Exception{
        User testUser = new User(phoneForTests);

        Assert.assertFalse(ussdLocationServicesBroker.addUssdLocationLookupAllowed(testUser.getUid(), UserInterfaceType.USSD));
    }
}
