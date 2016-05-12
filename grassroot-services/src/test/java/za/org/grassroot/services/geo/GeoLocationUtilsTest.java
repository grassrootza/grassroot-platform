package za.org.grassroot.services.geo;

import com.google.common.collect.Lists;
import org.junit.Test;
import za.org.grassroot.core.domain.geo.GeoLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

public class GeoLocationUtilsTest {

	@Test
	public void testCenterCalculation1() {
		ArrayList<GeoLocation> locations = Lists.newArrayList(
				new GeoLocation(50.00, 40.00),
				new GeoLocation(70.00, 40.00),
				new GeoLocation(70.00, 40.00)
		);
		GeoLocation centralLocation = GeoLocationUtils.centralLocation(locations);
		assertEquals(63.33, centralLocation.getLatitude(), 0.01);
		assertEquals(40, centralLocation.getLongitude(), 0.001);
	}

	@Test
	public void testCenterCalculation2() {
		List<GeoLocation> locations = new ArrayList<>();
		locations.add(new GeoLocation(121.612016711363d, 32));
		locations.add(new GeoLocation(121.849004871905, 31));
		GeoLocation centralLocation = GeoLocationUtils.centralLocation(locations);
		assertEquals(121.73, centralLocation.getLatitude(), 0.001);
		assertEquals(31.5, centralLocation.getLongitude(), 0.1);
	}

	@Test
	public void testCenterCalculationWithEmptyInput() {
		GeoLocation centralLocation = GeoLocationUtils.centralLocation(Collections.emptyList());
		assertNull(centralLocation);
	}
}
