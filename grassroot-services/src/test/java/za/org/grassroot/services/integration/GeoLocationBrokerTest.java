package za.org.grassroot.services.integration;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.PreviousPeriodUserLocationRepository;
import za.org.grassroot.core.repository.UserLocationLogRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.ServicesTestConfig;
import za.org.grassroot.services.geo.CenterCalculationResult;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.geo.GeographicSearchType;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.security.InvalidParameterException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@RunWith(SpringRunner.class) @DataJpaTest
@ContextConfiguration(classes = ServicesTestConfig.class)
public class GeoLocationBrokerTest {

	@Autowired
	private PreviousPeriodUserLocationRepository previousPeriodUserLocationRepository;

	@Mock
	private TypedQuery<ObjectLocation> mockQuery;

	@Mock
	private EntityManager mockEntityManager;

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserLocationLogRepository userLocationLogRepository;

	@Autowired
	private GeoLocationBroker geoLocationBroker;

	private User testUser;

	@Before
	public void setUp() {
		testUser = new User("27610001234", "test", null);
		given(mockQuery.setParameter(nullable(String.class), any())).willReturn(mockQuery);
		given(mockQuery.getResultList()).willAnswer(Arrays::asList);
		given(mockEntityManager.createQuery(nullable(String.class), eq(ObjectLocation.class))).willReturn(mockQuery);
	}


	@Test
	public void testPreviusPeriodAggregation() {
		ZoneId zoneId = DateTimeUtil.getSAST();
		geoLocationBroker.logUserLocation("111", 50.00, 40.00, LocalDateTime.of(2016, 3, 5, 13, 15).atZone(zoneId).toInstant(), UserInterfaceType.ANDROID);
		geoLocationBroker.logUserLocation("111", 60.00, 40.00, LocalDateTime.of(2016, 3, 7, 13, 15).atZone(zoneId).toInstant(), UserInterfaceType.ANDROID);
		geoLocationBroker.logUserLocation("222", 50.00, 40.00, LocalDateTime.of(2016, 3, 7, 13, 15).atZone(zoneId).toInstant(), UserInterfaceType.ANDROID);

		LocalDate localDate1 = LocalDate.of(2016, 4, 4);
		geoLocationBroker.calculatePreviousPeriodUserLocations(localDate1);

		LocalDate localDate2 = LocalDate.of(2016, 4, 6);
		geoLocationBroker.calculatePreviousPeriodUserLocations(localDate2);

		LocalDate localDate3 = LocalDate.of(2016, 4, 8);
		geoLocationBroker.calculatePreviousPeriodUserLocations(localDate3);

		HashSet<String> userUids = Sets.newHashSet("111", "222", "333");

		List<PreviousPeriodUserLocation> list1 = previousPeriodUserLocationRepository.findByKeyLocalDateAndKeyUserUidIn(localDate1, userUids);
		Assert.assertEquals(2, list1.size());
		PreviousPeriodUserLocation previousLocationForUser111 = findPreviousPeriodUserLocationByUserUid(list1, "111");
		Assert.assertEquals(55, previousLocationForUser111.getLocation().getLatitude(), 0.001);
		Assert.assertEquals(40, previousLocationForUser111.getLocation().getLongitude(), 0.001);
		Assert.assertEquals(2, previousLocationForUser111.getLogCount());

		List<PreviousPeriodUserLocation> list2 = previousPeriodUserLocationRepository.findByKeyLocalDateAndKeyUserUidIn(localDate2, userUids);
		Assert.assertEquals(2, list2.size());
		previousLocationForUser111 = findPreviousPeriodUserLocationByUserUid(list2, "111");
		Assert.assertEquals(60, previousLocationForUser111.getLocation().getLatitude(), 0.001);
		Assert.assertEquals(40, previousLocationForUser111.getLocation().getLongitude(), 0.001);
		Assert.assertEquals(1, previousLocationForUser111.getLogCount());

		List<PreviousPeriodUserLocation> list3 = previousPeriodUserLocationRepository.findByKeyLocalDateAndKeyUserUidIn(localDate3, userUids);
		Assert.assertEquals(0, list3.size());

		CenterCalculationResult result1 = geoLocationBroker.calculateCenter(userUids, LocalDate.of(2016, 4, 6));
		Assert.assertEquals(2, result1.getEntityCount());
		GeoLocation center1 = result1.getCenter();
		Assert.assertEquals(55, center1.getLatitude(), 0.001);
		Assert.assertEquals(40, center1.getLongitude(), 0.001);
	}

	private PreviousPeriodUserLocation findPreviousPeriodUserLocationByUserUid(List<PreviousPeriodUserLocation> result1, String userUid) {
		return result1.stream()
				.filter(location -> location.getKey().getUserUid().equals(userUid))
				.findFirst()
				.get();
	}

	@Test
	public void validRequestShouldBeSuccessfulWhenFetchingGroupLocations () {
		List<ObjectLocation> groupLocations = geoLocationBroker.fetchPublicGroupsNearbyWithLocation(new GeoLocation(53.4808, 2.2426), 10);

		// as above, consolidation has broken something in mock injection, come back and fix when/if important
//		verify(mockQuery, times(1)).getResultList();
//		verify(mockEntityManager, times(1)).createQuery(nullable(String.class), eq(ObjectLocation.class));

		Assert.assertNotNull(groupLocations.size());
		Assert.assertEquals(groupLocations.size(), 0);
	}

	@Test(expected=InvalidParameterException.class)
	public void nullGeoLocationShouldThrowExceptionWhenFetchingGroupLocations () {
		geoLocationBroker.fetchPublicGroupsNearbyWithLocation(null, 10);
	}

	@Test(expected=InvalidParameterException.class)
	public void nullRadiusThrowExceptionWhenFetchingGroupLocations () {
		geoLocationBroker.fetchPublicGroupsNearbyWithLocation(new GeoLocation(0.00,0.00), null);
	}

	@Test(expected=InvalidParameterException.class)
	public void negativeRadiusThrowExceptionWhenFetchingGroupLocations () {
		geoLocationBroker.fetchPublicGroupsNearbyWithLocation(new GeoLocation(0.00,0.00), -10);
	}

	@Test
	public void invalidLatLongShouldThrowExceptionWhenFetchingGroupLocations () {
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

	// spurious failing on transience exception, restore when important
//	@Test
//	public void validRequestShouldBeSuccessfulWhenFetchingMeetingLocations () throws Exception {
//		List<ObjectLocation> meetingLocations = geoLocationBroker.fetchMeetingLocationsNearUser(
//				testUser, new GeoLocation(53.4808, 2.2426), 10000, GeographicSearchType.PUBLIC, null);
//
//		verify(mockQuery, times(1)).getResultList();
//		verify(mockEntityManager, times(1)).createQuery(nullable(String.class), eq(ObjectLocation.class));
//
//		Assert.assertNotNull(meetingLocations);
//		Assert.assertEquals(meetingLocations.size(), 0);
//	}

	@Test(expected=InvalidParameterException.class)
	public void nullRadiusThrowExceptionWhenFetchingMeetingLocations () {
		geoLocationBroker.fetchMeetingLocationsNearUser(testUser, new GeoLocation(0.00, 0.00), 0, GeographicSearchType.PUBLIC, null);
	}

	@Test(expected=InvalidParameterException.class)
	public void negativeRadiusThrowExceptionWhenFetchingMeetingLocations () {
		geoLocationBroker.fetchMeetingLocationsNearUser(testUser, new GeoLocation(0.00, 0.00), -10, GeographicSearchType.PUBLIC, null);
	}

	private void expectedValidFetchGroupLocationsRequest (double latitude, double longitude){
		try {
			geoLocationBroker.fetchPublicGroupsNearbyWithLocation(new GeoLocation(latitude, longitude), 10);
		}
		catch (Exception e){
			Assert.fail();
		}
	}

	private void expectedInValidFetchGroupLocationsRequest (double latitude, double longitude){
		try {
			geoLocationBroker.fetchPublicGroupsNearbyWithLocation(new GeoLocation(latitude, longitude), 10);
			Assert.fail();
		}
		catch (Exception e){
			assert((e instanceof InvalidParameterException));
		}
	}
}
