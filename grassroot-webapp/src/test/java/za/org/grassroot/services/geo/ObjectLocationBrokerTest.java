package za.org.grassroot.services.geo;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.webapp.controller.rest.livewire.LocationRestController;

import javax.persistence.EntityManager;

@RunWith(MockitoJUnitRunner.class)
@ContextConfiguration
public class ObjectLocationBrokerTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ObjectLocationBrokerImpl objectLocationBroker;

    @Before
    public void setUp () {
        MockitoAnnotations.initMocks(this);
        MockMvcBuilders.standaloneSetup(objectLocationBroker).build();
    }

    @Test
    @Ignore
    public void validRequestShouldReturnSuccess () throws Exception {
        objectLocationBroker.fetchGroupLocations(null, null);
    }
}
