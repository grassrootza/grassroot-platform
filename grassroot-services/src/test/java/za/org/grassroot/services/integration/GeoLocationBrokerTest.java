package za.org.grassroot.services.integration;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.repository.PreviousPeriodUserLocationRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.geo.CenterCalculationResult;
import za.org.grassroot.services.geo.GeoLocationBroker;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfig.class)
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class GeoLocationBrokerTest {
	@Autowired
	private GeoLocationBroker geoLocationBroker;

	@Autowired
	private PreviousPeriodUserLocationRepository previousPeriodUserLocationRepository;

	@Test
	public void testPreviusPeriodAggregation() {
		ZoneId zoneId = DateTimeUtil.getSAST();
		geoLocationBroker.logUserLocation("111", 50.00, 40.00, LocalDateTime.of(2016, 3, 5, 13, 15).atZone(zoneId).toInstant());
		geoLocationBroker.logUserLocation("111", 60.00, 40.00, LocalDateTime.of(2016, 3, 7, 13, 15).atZone(zoneId).toInstant());
		geoLocationBroker.logUserLocation("222", 50.00, 40.00, LocalDateTime.of(2016, 3, 7, 13, 15).atZone(zoneId).toInstant());

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
}
