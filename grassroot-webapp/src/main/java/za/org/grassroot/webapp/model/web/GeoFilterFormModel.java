package za.org.grassroot.webapp.model.web;

import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.services.group.GroupLocationFilter;

public class GeoFilterFormModel {

    private boolean filterByGroupSize;
    private boolean filterByAgeOfGroup;
    private boolean filterByTaskNumber;
    private boolean filterByLocationAndRadius;

    private int minGroupSize;
    private int minGroupAgeWeeks;
    private int minTaskNumber;
    private int searchRadius;
    private GeoLocation location;

    public GeoFilterFormModel() {
        // For JPA
    }

    public GeoFilterFormModel(GeoLocation location, int searchRadius) {
        this.location = location;
        this.searchRadius = searchRadius;
    }

    public static GroupLocationFilter convertToFilter(GeoFilterFormModel formModel) {
        // todo: make sure to intercept/handle zero radius with non-null location
        GeoLocation location = formModel.filterByLocationAndRadius ? formModel.location : null;
        int filterRadius = formModel.filterByLocationAndRadius ? formModel.searchRadius : 0;
        GroupLocationFilter filter = new GroupLocationFilter(location, filterRadius, false);

        filter.setMinimumGroupSize(formModel.filterByGroupSize ? formModel.minGroupSize : null);
        filter.setMinimumGroupLifeWeeks(formModel.filterByAgeOfGroup ? formModel.minGroupAgeWeeks : null);
        filter.setMinimumGroupTasks(formModel.filterByTaskNumber ? formModel.minTaskNumber : null);

        return filter;
    }

    public boolean isFilterByGroupSize() {
        return filterByGroupSize;
    }

    public void setFilterByGroupSize(boolean filterByGroupSize) {
        this.filterByGroupSize = filterByGroupSize;
    }

    public boolean isFilterByAgeOfGroup() {
        return filterByAgeOfGroup;
    }

    public void setFilterByAgeOfGroup(boolean filterByAgeOfGroup) {
        this.filterByAgeOfGroup = filterByAgeOfGroup;
    }

    public boolean isFilterByTaskNumber() {
        return filterByTaskNumber;
    }

    public void setFilterByTaskNumber(boolean filterByTaskNumber) {
        this.filterByTaskNumber = filterByTaskNumber;
    }

    public boolean isFilterByLocationAndRadius() {
        return filterByLocationAndRadius;
    }

    public void setFilterByLocationAndRadius(boolean filterByLocationAndRadius) {
        this.filterByLocationAndRadius = filterByLocationAndRadius;
    }

    public int getMinGroupSize() {
        return minGroupSize;
    }

    public void setMinGroupSize(int minGroupSize) {
        this.minGroupSize = minGroupSize;
    }

    public int getMinGroupAgeWeeks() {
        return minGroupAgeWeeks;
    }

    public void setMinGroupAgeWeeks(int minGroupAgeWeeks) {
        this.minGroupAgeWeeks = minGroupAgeWeeks;
    }

    public int getMinTaskNumber() {
        return minTaskNumber;
    }

    public void setMinTaskNumber(int minTaskNumber) {
        this.minTaskNumber = minTaskNumber;
    }

    public int getSearchRadius() {
        return searchRadius;
    }

    public void setSearchRadius(int searchRadius) {
        this.searchRadius = searchRadius;
    }

    public GeoLocation getLocation() {
        return location;
    }

    public void setLocation(GeoLocation location) {
        this.location = location;
    }
}
