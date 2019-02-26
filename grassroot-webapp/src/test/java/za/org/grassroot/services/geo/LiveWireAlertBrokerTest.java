package za.org.grassroot.services.geo;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationEventPublisher;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.repository.ConfigRepository;
import za.org.grassroot.core.repository.DataSubscriberRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.LiveWireAlertRepository;
import za.org.grassroot.core.repository.MeetingRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBrokerImpl;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class LiveWireAlertBrokerTest {

    private LiveWireAlertBrokerImpl liveWireAlertBroker;

    @Mock
    private LiveWireAlertRepository alertRepositoryMock;
    @Mock
    private UserRepository userRepositoryMock;
    @Mock
    private GroupRepository groupRepositoryMock;
    @Mock
    private MeetingRepository meetingRepositoryMock;
    @Mock
    private DataSubscriberRepository dataSubscriberRepositoryMock;
    @Mock
    private EntityManager entityManagerMock;
    @Mock
    private ApplicationEventPublisher applicationEventPublisherMock;
    @Mock
    private LogsAndNotificationsBroker logsAndNotificationsBrokerMock;
    @Mock
    private PermissionBroker permissionBrokerMock;

    @Mock
    private TypedQuery<LiveWireAlert> mockQuery;

    @Mock
    private ConfigRepository configRepositoryMock;

    private User testUser;
    private Integer testRadius = 5;
    private GeoLocation testLocation;

    @Before
    public void setUp () {
        liveWireAlertBroker = new LiveWireAlertBrokerImpl(alertRepositoryMock,userRepositoryMock,
                groupRepositoryMock,meetingRepositoryMock,entityManagerMock,dataSubscriberRepositoryMock,
                applicationEventPublisherMock,logsAndNotificationsBrokerMock, permissionBrokerMock, configRepositoryMock);

        testUser = new User("1234567899","Testing", null);
        testLocation = new GeoLocation(-11.00, 11.0);
        given(mockQuery.setParameter(nullable(String.class),any())).willReturn(mockQuery);
        given(mockQuery.getResultList()).willAnswer(l -> Arrays.asList());
        given(entityManagerMock.createQuery(nullable(String.class),eq(LiveWireAlert.class))).willReturn(mockQuery);
    }

    @Test(expected = InvalidParameterException.class)
    public void nullOrInvalidLocationShouldThrowInvalidParameterException(){
        liveWireAlertBroker.fetchAlertsNearUser(testUser.getUid(),null, testRadius,GeographicSearchType.BOTH);
    }
    @Test(expected = InvalidParameterException.class)
    public void nullOrInvalidLocationWithPublicSearchTypeShouldThrowInvalidParameterException(){
        liveWireAlertBroker.fetchAlertsNearUser(testUser.getUid(),null, testRadius,GeographicSearchType.PUBLIC);
    }

    @Test(expected = InvalidParameterException.class)
    public void nullOrInvalidLocationWithPrivateSearchTypeShouldThrowInvalidParameterException(){
        liveWireAlertBroker.fetchAlertsNearUser(testUser.getUid(),null, testRadius,GeographicSearchType.PRIVATE);
    }


    @Test(expected = InvalidParameterException.class)
    public void negativeRadiusShouldThrowInvalidParameterException(){
        liveWireAlertBroker.fetchAlertsNearUser(testUser.getUid(),testLocation, -5,GeographicSearchType.BOTH);
    }

    @Test
    public void validRequestShouldBeSuccessfulWhenFetchingAlertsNearUser(){
        List<LiveWireAlert> liveWireAlerts =
                liveWireAlertBroker.fetchAlertsNearUser(testUser.getUid(),testLocation, testRadius,GeographicSearchType.BOTH);

        verify(mockQuery,times(1)).getResultList();
        verify(entityManagerMock, times(1)).createQuery(nullable(String.class), eq(LiveWireAlert.class));

        Assert.assertNotNull(liveWireAlerts);
        Assert.assertEquals(liveWireAlerts.size(), 0);
    }
}
