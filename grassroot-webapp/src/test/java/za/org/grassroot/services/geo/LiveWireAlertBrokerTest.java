package za.org.grassroot.services.geo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.ApplicationEventPublisher;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.services.livewire.LiveWireAlertBrokerImpl;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import java.security.InvalidParameterException;
import java.util.Arrays;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;

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
    private TypedQuery<LiveWireAlert> mockQuery;

    private User testUser;
    private Integer testRadius = 5;
    private String testCreatedByMe = "mine";
    private GeoLocation testLocation;
    private double testLat = -11.00,testLong = 11.0;

    @Before
    public void setUp () {
        liveWireAlertBroker = new LiveWireAlertBrokerImpl(alertRepositoryMock,userRepositoryMock,
                groupRepositoryMock,meetingRepositoryMock,entityManagerMock,dataSubscriberRepositoryMock,
                applicationEventPublisherMock,logsAndNotificationsBrokerMock);

        testUser = new User("1234567899","Testing");
        testLocation = new GeoLocation(-11.0,11.0);
        given(mockQuery.setParameter(anyString(),any())).willReturn(mockQuery);
        given(mockQuery.getResultList()).willAnswer(l -> Arrays.asList());
        given(entityManagerMock.createQuery(anyString(),eq(LiveWireAlert.class))).willReturn(mockQuery);
    }

    @Test(expected = InvalidParameterException.class)
    public void nullOrInvalidLocationShouldThrowInvalidParameterException(){
        liveWireAlertBroker.fetchAlertsNearUser(testUser.getUid(),null,testCreatedByMe,testRadius,GeographicSearchType.BOTH);
    }
}
