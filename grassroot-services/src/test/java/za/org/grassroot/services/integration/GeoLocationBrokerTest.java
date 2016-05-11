package za.org.grassroot.services.integration;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.core.GrassRootApplicationProfiles;
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

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {GrassRootServicesConfig.class, TestContextConfig.class})
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
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
		Assert.assertEquals(list1.size(), 2);
		PreviousPeriodUserLocation previousLocationForUser111 = findPreviousPeriodUserLocationByUserUid(list1, "111");
		Assert.assertEquals((int) previousLocationForUser111.getLocation().getLatitude(), 55);
		Assert.assertEquals((int) previousLocationForUser111.getLocation().getLongitude(), 39);
		Assert.assertEquals(previousLocationForUser111.getLogCount(), 2);

		List<PreviousPeriodUserLocation> list2 = previousPeriodUserLocationRepository.findByKeyLocalDateAndKeyUserUidIn(localDate2, userUids);
		Assert.assertEquals(list2.size(), 2);
		previousLocationForUser111 = findPreviousPeriodUserLocationByUserUid(list2, "111");
		Assert.assertEquals((int) previousLocationForUser111.getLocation().getLatitude(), 60);
		Assert.assertEquals((int) previousLocationForUser111.getLocation().getLongitude(), 40);
		Assert.assertEquals((int) previousLocationForUser111.getLogCount(), 1);

		List<PreviousPeriodUserLocation> list3 = previousPeriodUserLocationRepository.findByKeyLocalDateAndKeyUserUidIn(localDate3, userUids);
		Assert.assertEquals(list3.size(), 0);

		CenterCalculationResult result1 = geoLocationBroker.calculateCenter(userUids, LocalDate.of(2016, 4, 6));
		Assert.assertEquals(result1.getUserCount(), 2);
		GeoLocation center1 = result1.getCenter();
		Assert.assertEquals((int) center1.getLatitude(), 55);
		Assert.assertEquals((int) center1.getLongitude(), 39);
	}

	private PreviousPeriodUserLocation findPreviousPeriodUserLocationByUserUid(List<PreviousPeriodUserLocation> result1, String userUid) {
		return result1.stream()
				.filter(location -> location.getKey().getUserUid().equals(userUid))
				.findFirst()
				.get();
	}
}
