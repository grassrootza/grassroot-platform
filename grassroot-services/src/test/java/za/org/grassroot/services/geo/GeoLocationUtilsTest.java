package za.org.grassroot.services.geo;

import com.google.common.collect.Lists;
import org.junit.Test;
import za.org.grassroot.core.domain.geo.GeoLocation;

import java.util.ArrayList;

import static junit.framework.Assert.assertEquals;

public class GeoLocationUtilsTest {

	@Test
	public void testCenterCalculation() {
		ArrayList<GeoLocation> locations = Lists.newArrayList(
				new GeoLocation(50.00, 40.00),
				new GeoLocation(70.00, 40.00),
				new GeoLocation(70.00, 40.00)
		);
		GeoLocation centralLocation = GeoLocationUtils.centralLocation(locations);
		assertEquals(63, (int) centralLocation.getLatitude());
		assertEquals(39, (int) centralLocation.getLongitude());
	}
}
