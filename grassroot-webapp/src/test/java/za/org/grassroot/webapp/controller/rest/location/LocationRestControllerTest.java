package za.org.grassroot.webapp.controller.rest.location;

import com.jayway.jsonpath.JsonPath;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.geo.GeographicSearchType;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.webapp.controller.rest.RestAbstractUnitTest;

import java.security.InvalidParameterException;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LocationRestControllerTest extends RestAbstractUnitTest {
    private final static String uri = "/api/location/list";

    @Autowired
    protected WebApplicationContext webApplicationContext;

    @Mock
    private ObjectLocationBroker objectLocationBroker;

    @Mock
    private GeoLocationBroker geoLocationBroker;

    @InjectMocks
    private LocationRestController controller;

    @Before
    public void setUp () {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /*
    Return to and fix these when have time and they are significant again

    @Test
    public void validFullRequestShouldReturn200 () throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders
                .get(uri + "?latitude=1&longitude=1&restriction=0&radius=3&token=234324")
                .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        Assert.assertEquals("failure - expected HTTP status 200", 200, result.getResponse().getStatus());
    }

    @Test
    public void radiusParameterShouldBeOptional () throws Exception {
        MvcResult result = mockMvc.perform(
                MockMvcRequestBuilders
                        .get(uri + "?latitude=1&longitude=1&restriction=0&token=234324")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        Assert.assertEquals("failure - expected HTTP status 200", 200, result.getResponse().getStatus());
    }

    @Test
    public void restrictionParameterShouldBeOptional () throws Exception {
        MvcResult result = mockMvc.perform(
                MockMvcRequestBuilders
                        .get(uri + "?latitude=1&longitude=1&radius=1&token=234324")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        Assert.assertEquals("failure - expected HTTP status 200", 200, result.getResponse().getStatus());
    }
    */

    @Test
    public void invalidLatitudeShouldReturn400 () throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders
                .get(uri + "?latitude=200&longitude=0&radius=3&token=234324")
                .accept(MediaType.APPLICATION_JSON))
                .andReturn();

        Assert.assertEquals("failure - expected HTTP status 400", 400, result.getResponse().getStatus());
        Assert.assertTrue("failure - expected HTTP response body to contain content",
                result.getResponse().getContentAsString().trim().length() > 0);

        String responseMessage = JsonPath.read(result.getResponse().getContentAsString(), "$.message");
        Assert.assertEquals("failure - expected ", responseMessage, "INVALID_LOCATION_LATLONG_PARAMETER");
    }

    @Test
    public void invalidLongitudeShouldReturn400 () throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders
                .get(uri + "?latitude=0&longitude=200&radius=3&token=234324")
                .accept(MediaType.APPLICATION_JSON))
                .andReturn();

        Assert.assertEquals("failure - expected HTTP status 400", 400, result.getResponse().getStatus());
        Assert.assertTrue("failure - expected HTTP response body to contain content",
                result.getResponse().getContentAsString().trim().length() > 0);

        String responseMessage = JsonPath.read(result.getResponse().getContentAsString(), "$.message");
        Assert.assertEquals("failure - expected ", responseMessage, "INVALID_LOCATION_LATLONG_PARAMETER");
    }

    @Test
    public void invalidRadiusShouldReturn400 () throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders
                .get(uri + "?latitude=0&longitude=0&radius=0&token=234324")
                .accept(MediaType.APPLICATION_JSON))
                .andReturn();

        Assert.assertEquals("failure - expected HTTP status 400", 400, result.getResponse().getStatus());
        Assert.assertTrue("failure - expected HTTP response body to contain content",
                result.getResponse().getContentAsString().trim().length() > 0);

        String responseMessage = JsonPath.read(result.getResponse().getContentAsString(), "$.message");
        Assert.assertEquals("failure - expected ", responseMessage, "INVALID_LOCATION_RADIUS_PARAMETER");
    }

    @Test
    public void erroringFetchMeetingLocationsShouldReturn500 () throws Exception {
        when(objectLocationBroker.fetchMeetingLocationsNearUser(any(User.class), any(GeoLocation.class), any(Integer.class),
                eq(GeographicSearchType.PUBLIC), eq(null))).thenThrow
                (InvalidParameterException.class);
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders
                .get(uri + "?latitude=0&longitude=0&radius=3&token=234324")
                .accept(MediaType.APPLICATION_JSON))
                .andReturn();

        Assert.assertEquals("failure - expected HTTP status 500", 500, result.getResponse().getStatus());
    }


}
