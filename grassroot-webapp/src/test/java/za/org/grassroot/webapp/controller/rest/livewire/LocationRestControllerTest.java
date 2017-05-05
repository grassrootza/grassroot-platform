package za.org.grassroot.webapp.controller.rest.livewire;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.geo.ObjectLocationBroker;

@RunWith(MockitoJUnitRunner.class)
public class LocationRestControllerTest {
    protected MockMvc mvc;

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
        MockitoAnnotations.initMocks(this);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void validRequestShouldReturn200 () throws Exception {
        MvcResult result = mvc
                .perform(MockMvcRequestBuilders
                        .get("/api/location/list")
                        .param("latitude", "0")
                        .param("longitude", "0")
                        .param("radius", "0")
                        .param("token", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();

        int status = result.getResponse().getStatus();

        Assert.assertEquals("failure - expected HTTP status 200", 200, status);
    }
}
