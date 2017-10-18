package za.org.grassroot.webapp.controller.rest.location;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.geo.GeographicSearchType;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.webapp.controller.rest.RestAbstractUnitTest;

public class AroundMeRestControllerTest extends RestAbstractUnitTest {

    @InjectMocks
    private AroundMeRestController aroundMeRestController;

    @Mock
    private ObjectLocationBroker objectLocationBroker;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LiveWireAlertBroker liveWireAlertBroker;

    private String path = "/api/location";
    private String uidParameter = "userUid";
    private String testFilterTerm = "filterTerm";

    private int testRadiusMetres = 5;


    private static final double testLat = -11.00;
    private static final double testLong = 11.00;

    @Before
    public void setUp(){
        mockMvc = MockMvcBuilders.standaloneSetup(aroundMeRestController).build();
    }

    @Test
    public void fetchAllEntitiesNearUserShouldWork() throws Exception{
        GeoLocation testLocation = new GeoLocation(testLat,testLong);
        User user = userRepository.findOneByUid(uidParameter);
        String testSearchTerm = "searchTerm";

        Assert.assertNotNull(objectLocationBroker.fetchGroupsNearby(testLocation,testRadiusMetres,testSearchTerm,testFilterTerm,uidParameter));
        Assert.assertNotNull(objectLocationBroker.fetchMeetingLocationsNearUser(user,testLocation,testRadiusMetres, GeographicSearchType.PUBLIC,testSearchTerm));
    }

    @Test
    public void getPublicGroupsNearUserShouldWork() throws Exception{
        GeoLocation testLocation = new GeoLocation(testLat,testLong);

        Assert.assertNotNull(objectLocationBroker.fetchUserGroupsNearThem(uidParameter,testLocation,testRadiusMetres,testFilterTerm));
    }

    @Test
    public void getAlertsNearUserShouldWork() throws Exception{
        GeoLocation testLocation = new GeoLocation(testLat,testLong);
        String testCreatedByMe = "createdByMe";

        Assert.assertNotNull(liveWireAlertBroker.fetchAlertsNearUser(uidParameter,testLocation,testCreatedByMe,testRadiusMetres));
    }

}
