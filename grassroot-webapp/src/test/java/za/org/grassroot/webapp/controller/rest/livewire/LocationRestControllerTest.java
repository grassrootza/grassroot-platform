package za.org.grassroot.webapp.controller.rest.livewire;

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
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.webapp.controller.rest.RestAbstractUnitTest;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.any;
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

    @Test
    public void validRequestShouldReturn200 () throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders
                .get(uri + "?latitude=1&longitude=1&radius=3&token=234324")
                .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        Assert.assertEquals("failure - expected HTTP status 200", 200, result.getResponse().getStatus());
    }

    @Test
    public void radiusParameterShouldBeOptional () throws Exception {
        MvcResult result = mockMvc.perform(
                MockMvcRequestBuilders
                        .get(uri + "?latitude=1&longitude=1&token=234324")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        Assert.assertEquals("failure - expected HTTP status 200", 200, result.getResponse().getStatus());
    }

    @Test
    public void t () throws Exception {
        //        String json = new String(readAllBytes(Paths.get(ClassLoader.getSystemResource("input_post_json_sample/dtt/valid.json").toURI())));
        //        when(service.retrieveTriggerJsonString(any(String.class))).thenReturn(json);

        List<ObjectLocation> locationList = new ArrayList<>();
        locationList.add(new ObjectLocation("uuid", "name", 1, 1, 1, "type", true));

        when(objectLocationBroker.fetchGroupLocations(any(GeoLocation.class), any(Integer.class))).thenReturn(locationList);

        MvcResult result = mockMvc.perform(
                MockMvcRequestBuilders.get(uri)
                        .param("latitude", "30.5595")
                        .param("longitude", "22.9375")
                        .param("radius", "5")
                        .param("token", "1234")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();

        int status = result.getResponse().getStatus();
        System.out.println(result.getResponse());

        Assert.assertEquals("failure - expected HTTP status 200", 200, status);
    }
}
