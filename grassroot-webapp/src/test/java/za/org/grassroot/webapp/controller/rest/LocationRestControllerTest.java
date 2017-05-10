package za.org.grassroot.webapp.controller.rest;

import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.webapp.controller.rest.livewire.LocationRestController;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.file.Paths;

import static org.mockito.Mockito.*;
import static java.nio.file.Files.readAllBytes;

/**
 * TODO - Finish this unit test
 */
@RunWith(MockitoJUnitRunner.class)
@ContextConfiguration
public class LocationRestControllerTest {
    private MockMvc mvc;

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
    public void validRequestShouldReturnSuccess () throws Exception {
        String uri = "/api/location/list?latitude=1&longitude=1&radius=2&token=234324";
        MvcResult result = mvc.perform(MockMvcRequestBuilders.get(uri).accept(MediaType.APPLICATION_JSON)).andReturn();
        Assert.assertEquals("failure - expected HTTP status 200", 200, result.getResponse().getStatus());
    }

    @Test
    public void radiusParameterShouldBeOptional () throws Exception {
        String uri = "/api/location/list?latitude=1&longitude=1&token=234324";
        MvcResult result = mvc.perform(MockMvcRequestBuilders.get(uri).accept(MediaType.APPLICATION_JSON)).andReturn();
        Assert.assertEquals("failure - expected HTTP status 200", 200, result.getResponse().getStatus());
    }

    @Test
    public void t () throws Exception {
        //        String json = new String(readAllBytes(Paths.get(ClassLoader.getSystemResource("input_post_json_sample/dtt/valid.json").toURI())));
        //        when(service.retrieveTriggerJsonString(any(String.class))).thenReturn(json);

        String uri = "/api/location/list?latitude=30.5595&longitude=22.9375&token=234324";
        MvcResult result = mvc.perform(MockMvcRequestBuilders.get(uri).accept(MediaType.APPLICATION_JSON)).andReturn();

        int status = result.getResponse().getStatus();
        System.out.println(result.getResponse());

        Assert.assertEquals("failure - expected HTTP status 200", 200, status);
    }
}
