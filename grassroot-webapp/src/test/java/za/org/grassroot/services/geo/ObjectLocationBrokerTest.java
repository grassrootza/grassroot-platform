package za.org.grassroot.services.geo;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.web.client.RestTemplate;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.repository.GroupLocationRepository;
import za.org.grassroot.core.repository.MeetingLocationRepository;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ObjectLocationBrokerTest {
    @Mock
    private TypedQuery<ObjectLocation> mockQuery;

    @Mock
    private EntityManager mockEntityManager;

    @Mock
    private GroupLocationRepository mockGroupLocationRepository;

    @Mock
    private MeetingLocationRepository mockMeetingLocationRepository;

    @Mock
    private RestTemplate mockRestTemplate;

    private ObjectLocationBrokerImpl objectLocationBroker;

    @Before
    public void setUp () {
        objectLocationBroker = new ObjectLocationBrokerImpl(mockEntityManager, mockGroupLocationRepository,
                mockMeetingLocationRepository, mockRestTemplate);

        given(mockQuery.setParameter(anyString(), any())).willReturn(mockQuery);
        given(mockQuery.getResultList()).willAnswer(i->Arrays.asList());
        given(mockEntityManager.createQuery(anyString(), eq(ObjectLocation.class))).willReturn(mockQuery);
    }

    @Test
    public void validRequestShouldBeSuccessfulWhenFetchingGroupLocations () throws Exception {
        List<ObjectLocation> groupLocations = objectLocationBroker.fetchGroupLocations(new GeoLocation(53.4808, 2.2426), 10);

        verify(mockQuery, times(1)).getResultList();
        verify(mockEntityManager, times(1)).createQuery(anyString(), eq(ObjectLocation.class));

        Assert.assertNotNull(groupLocations.size());
        Assert.assertEquals(groupLocations.size(), 0);
    }

    @Test(expected=InvalidParameterException.class)
    public void nullGeoLocationShouldThrowExceptionWhenFetchingGroupLocations () throws Exception {
        objectLocationBroker.fetchGroupLocations(null, 10);
    }

    @Test(expected=InvalidParameterException.class)
    public void nullRadiusThrowExceptionWhenFetchingGroupLocations () throws Exception {
        objectLocationBroker.fetchGroupLocations(new GeoLocation(0.00,0.00), null);
    }

    @Test(expected=InvalidParameterException.class)
    public void negativeRadiusThrowExceptionWhenFetchingGroupLocations () throws Exception {
        objectLocationBroker.fetchGroupLocations(new GeoLocation(0.00,0.00), -10);
    }

    @Test
    public void invalidLatLongShouldThrowExceptionWhenFetchingGroupLocations () throws Exception {
        expectedValidFetchGroupLocationsRequest(0.00, 0.0);
        expectedValidFetchGroupLocationsRequest(-90.00, 0.0);
        expectedValidFetchGroupLocationsRequest(90.00, 0.0);
        expectedValidFetchGroupLocationsRequest(0.00, 180.0);
        expectedValidFetchGroupLocationsRequest(0.00, -180.0);

        expectedInValidFetchGroupLocationsRequest(-99.00, 0.0);
        expectedInValidFetchGroupLocationsRequest(99.00, 0.0);
        expectedInValidFetchGroupLocationsRequest(0.00, 189.0);
        expectedInValidFetchGroupLocationsRequest(0.00, -189.0);
    }

    @Test
    public void validRequestShouldBeSuccessfulWhenFetchingMeetingLocations () throws Exception {
        List<ObjectLocation> groupLocations = objectLocationBroker.fetchMeetingLocations(new GeoLocation(53.4808, 2.2426), 10, 0);

        verify(mockQuery, times(1)).getResultList();
        verify(mockEntityManager, times(1)).createQuery(anyString(), eq(ObjectLocation.class));

        Assert.assertNotNull(groupLocations.size());
        Assert.assertEquals(groupLocations.size(), 0);
    }

    @Test(expected=InvalidParameterException.class)
    public void nullGeoLocationShouldThrowExceptionWhenFetchingMeetingLocations () throws Exception {
        objectLocationBroker.fetchMeetingLocations(null, 10, null);
    }

    @Test(expected=InvalidParameterException.class)
    public void nullRadiusThrowExceptionWhenFetchingMeetingLocations () throws Exception {
        objectLocationBroker.fetchMeetingLocations(new GeoLocation(0.00, 0.00), 0, 0);
    }

    @Test(expected=InvalidParameterException.class)
    public void negativeRadiusThrowExceptionWhenFetchingMeetingLocations () throws Exception {
        objectLocationBroker.fetchMeetingLocations(new GeoLocation(0.00, 0.00), -10, null);
    }

    private void expectedValidFetchGroupLocationsRequest (double latitude, double longitude){
        try {
            objectLocationBroker.fetchGroupLocations(new GeoLocation(latitude, longitude), 10);
        }
        catch (Exception e){
            Assert.fail();
        }
    }

    private void expectedInValidFetchGroupLocationsRequest (double latitude, double longitude){
        try {
            objectLocationBroker.fetchGroupLocations(new GeoLocation(latitude, longitude), 10);
            Assert.fail();
        }
        catch (Exception e){
            assert((e instanceof InvalidParameterException));
        }
    }
}
