package za.org.grassroot.services.geo;

import za.org.grassroot.core.domain.geo.GeoLocation;

public class CenterCalculationResult {
	private final int entityCount;
	private GeoLocation center;

	public CenterCalculationResult(int userCount, GeoLocation center) {
		this.entityCount = userCount;
		if (isDefined() && center == null) {
			throw new IllegalArgumentException("Center is null although user count is " + userCount);
		}
		this.center = center;
	}

	public int getEntityCount() {
		return entityCount;
	}

	public GeoLocation getCenter() {
		return center;
	}

	public boolean isDefined() {
		return entityCount > 0;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("CenterCalculationResult{");
		sb.append("entityCount=").append(entityCount);
		sb.append(", center=").append(center);
		sb.append('}');
		return sb.toString();
	}
}
