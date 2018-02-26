package za.org.grassroot.services.campaign;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.Locale;

@Getter @AllArgsConstructor @ApiModel @Slf4j
public class CampaignActivityStatsRequest {

    // enums for these would clutter code, and only used by frontend, so just use constants
    public static final String BY_CHANNEL = "by_channel";
    public static final String BY_PROVINCE = "by_province";

    // then, for time period
    public static final String THIS_WEEK = "this_week";
    public static final String THIS_MONTH = "this_month";
    public static final String LAST_WEEK = "last_week";
    public static final String LAST_MONTH = "last_month";
    public static final String ALL_TIME = "all_time";

    @ApiModelProperty(allowableValues = "by_channel, by_province")
    private String datasetDivision;
    @ApiModelProperty(allowableValues = "this_week, this_month, last_week, last_month, all_time")
    private String timePeriod;

    public boolean isByChannel() {
        return BY_CHANNEL.equals(datasetDivision);
    }

    public Instant getStartTime(Instant campaignCreation) {
        log.info("getting start time, time period = {}", this.timePeriod);
        Instant calcTime;
        switch (timePeriod) {
            case LAST_WEEK:
                LocalDate lastWeek = LocalDate.now().minusWeeks(1);
                calcTime = lastWeek.with(dayOfWeek(), 1).atStartOfDay(Clock.systemDefaultZone().getZone()).toInstant();
                break;
            case LAST_MONTH:
                LocalDate lastMonth = LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth());
                calcTime = lastMonth.atStartOfDay(Clock.systemDefaultZone().getZone()).toInstant();
                break;
            case THIS_MONTH:
                calcTime = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(Clock.systemDefaultZone().getZone()).toInstant();
                break;
            case ALL_TIME:
                calcTime = campaignCreation;
                break;
            case THIS_WEEK:
            default:
                LocalDate now = LocalDate.now();
                TemporalField fieldISO = WeekFields.of(Locale.UK).dayOfWeek();
                calcTime = now.with(fieldISO, 1).atStartOfDay(Clock.systemDefaultZone().getZone()).toInstant();
        }
        return calcTime.isAfter(campaignCreation) ? calcTime : campaignCreation;
    }

    public Instant getEndTime(Instant campaignCreation) {
        Instant calcTime;
        switch (timePeriod) {
            case THIS_WEEK:
            case THIS_MONTH:
            case ALL_TIME:
                calcTime = Instant.now();
                break;
            case LAST_WEEK:
                calcTime = LocalDate.now().with(dayOfWeek(), 1).atStartOfDay(Clock.systemDefaultZone().getZone()).toInstant();
                break;
            case LAST_MONTH:
                LocalDate then = LocalDate.now().minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
                calcTime = then.atStartOfDay(Clock.systemDefaultZone().getZone()).toInstant();
                break;
            default:
                calcTime = Instant.now();
                break;
        }
        return calcTime.isAfter(campaignCreation) ? calcTime : campaignCreation;
    }

    private static TemporalField dayOfWeek() {
        return WeekFields.of(Locale.UK).dayOfWeek();
    }
}
