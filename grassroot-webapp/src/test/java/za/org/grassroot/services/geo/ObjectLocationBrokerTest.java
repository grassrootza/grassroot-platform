package za.org.grassroot.services.geo;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.runners.MockitoJUnitRunner;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.mockito.BDDMockito.*;


@RunWith(MockitoJUnitRunner.class)
public class ObjectLocationBrokerTest {

    @Mock
    private TypedQuery<ObjectLocation> mockQuery;

    @Mock
    private EntityManager mockEntityManager;

    private ObjectLocationBrokerImpl objectLocationBroker;

    @Before
    public void setUp () {
        objectLocationBroker = new ObjectLocationBrokerImpl(mockEntityManager);

        given(mockQuery.setParameter(anyString(), any())).willReturn(mockQuery);
        given(mockQuery.getResultList()).willAnswer(i->Arrays.asList());
        given(mockEntityManager.createQuery(anyString(), eq(ObjectLocation.class))).willReturn(mockQuery);
    }

    @Test
    public void validRequestShouldBeSuccessful () throws Exception {
        List<ObjectLocation> groupLocations = objectLocationBroker.fetchGroupLocations(new GeoLocation(53.4808, 2.2426), 10);

        verify(mockQuery, times(1)).setParameter(anyString(), any());
        verify(mockQuery, times(1)).getResultList();
        verify(mockEntityManager, times(1)).createQuery(anyString(), eq(ObjectLocation.class));

        Assert.assertNotNull(groupLocations.size());
        Assert.assertEquals(groupLocations.size(), 0);
    }

    @Test
    public void invalidLatLongShouldThrowException () throws Exception {

        //TODO
        //objectLocationBroker.fetchGroupLocations(new GeoLocation(200, 0), 10);

    }
}
