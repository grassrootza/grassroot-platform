package za.org.grassroot.services.group;

import za.org.grassroot.core.domain.geo.GeoLocation;

public class GroupLocationFilter {
	private GeoLocation center; // can be null
	private Integer radius; // can be null
	private boolean includeWithoutLocation;
	private Integer minimumGroupSize; // can be null
	private Integer minimumGroupLifeWeeks; // can be null
	private Integer minimumGroupTasks; // can be null

	public GroupLocationFilter(GeoLocation center, Integer radius, boolean includeWithoutLocation) {
		this.center = center;
		this.radius = radius;
		this.includeWithoutLocation = includeWithoutLocation;
	}

	public GeoLocation getCenter() {
		return center;
	}

	public void setCenter(GeoLocation center) {
		this.center = center;
	}

	public Integer getRadius() {
		return radius;
	}

	public void setRadius(Integer radius) {
		this.radius = radius;
	}

	public boolean isIncludeWithoutLocation() {
		return includeWithoutLocation;
	}

	public void setIncludeWithoutLocation(boolean includeWithoutLocation) {
		this.includeWithoutLocation = includeWithoutLocation;
	}

	public Integer getMinimumGroupSize() {
		return minimumGroupSize;
	}

	public void setMinimumGroupSize(Integer minimumGroupSize) {
		this.minimumGroupSize = minimumGroupSize;
	}

	public Integer getMinimumGroupLifeWeeks() {
		return minimumGroupLifeWeeks;
	}

	public void setMinimumGroupLifeWeeks(Integer minimumGroupLifeWeeks) {
		this.minimumGroupLifeWeeks = minimumGroupLifeWeeks;
	}

	public Integer getMinimumGroupTasks() {
		return minimumGroupTasks;
	}

	public void setMinimumGroupTasks(Integer minimumGroupTasks) {
		this.minimumGroupTasks = minimumGroupTasks;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("GroupLocationFilter{");
		sb.append("center=").append(center);
		sb.append(", radius=").append(radius);
		sb.append(", includeWithoutLocation=").append(includeWithoutLocation);
		sb.append('}');
		return sb.toString();
	}
}
