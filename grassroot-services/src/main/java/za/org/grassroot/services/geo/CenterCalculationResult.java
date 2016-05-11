package za.org.grassroot.services.geo;

import za.org.grassroot.core.domain.geo.GeoLocation;

import java.util.Objects;

public class CenterCalculationResult {
	private final int userCount;
	private GeoLocation center;

	public CenterCalculationResult(int userCount, GeoLocation center) {
		this.userCount = userCount;
		if (isDefined() && center == null) {
			throw new IllegalArgumentException("Center is null although user count is " + userCount);
		}
		this.center = center;
	}

	public int getUserCount() {
		return userCount;
	}

	public GeoLocation getCenter() {
		return center;
	}

	public boolean isDefined() {
		return userCount > 0;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("CenterCalculationResult{");
		sb.append("userCount=").append(userCount);
		sb.append(", center=").append(center);
		sb.append('}');
		return sb.toString();
	}
}
