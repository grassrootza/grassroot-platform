package za.org.grassroot.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.MeetingRepository;
import za.org.grassroot.core.specifications.EventSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.location.LocationInfoBroker;
import za.org.grassroot.services.geo.GeoLocationBroker;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.springframework.data.jpa.domain.Specification.where;

/**
 * Created by luke on 2017/04/05.
 */
@Component
@Slf4j
public class ScheduledGeoCalculations {

    private static final long DAYS_TO_CALC_MTGS = 7;

    private final GeoLocationBroker geoLocationBroker;
    private final GroupRepository groupRepository;
    private final MeetingRepository meetingRepository;
    private final LocationInfoBroker locationInfoBroker;

    @Autowired
    public ScheduledGeoCalculations(GeoLocationBroker geoLocationBroker,
                                    GroupRepository groupRepository,
                                    MeetingRepository meetingRepository,
                                    LocationInfoBroker locationInfoBroker) {
        this.geoLocationBroker = geoLocationBroker;
        this.groupRepository = groupRepository;
        this.meetingRepository = meetingRepository;
        this.locationInfoBroker = locationInfoBroker;
    }

    @Scheduled(cron = "0 0 2 * * *") // runs at 2am UTC every day
    public void calculateAggregateLocations() {
        // we had put few types of calculations here in sequence because one depends on
        // other being executed in order...

        LocalDate today = LocalDate.now();
        geoLocationBroker.calculatePreviousPeriodUserLocations(today);

        log.info("Calculating group locations for date {}", today);
        List<Group> groups = groupRepository.findAll();
        for (Group group : groups) {
            // we don't want one big TX for all groups, so we separate each group location
            // calculation into its own transaction
            geoLocationBroker.calculateGroupLocation(group.getUid(), today);
        }
    }

    @Scheduled(cron = "0 0 3 * * *") // runs at 3am UTC every day, so can assume above is done
    public void calculateMeetingLocations() {
        LocalDate today = LocalDate.now();
        // first, we get all public meetings, then we calculate their positions
        // (but do need to include meetings in recent past, as may have taken photo etc)
        Instant start = Instant.now().minus(DAYS_TO_CALC_MTGS, ChronoUnit.DAYS);
        Instant end = DateTimeUtil.getVeryLongAwayInstant(); // may change to 1 month in future

        List<Event> publicMeetings = meetingRepository.findAll(
                where(EventSpecifications.isPublic())
                .and(EventSpecifications.notCancelled())
                .and(EventSpecifications.startDateTimeBetween(start, end)));

        log.info("Calculating meeting locations for {} public meetings", publicMeetings.size());
        // as above: do this in separate TX rather than all as one
        publicMeetings.forEach(event -> {
            geoLocationBroker.calculateMeetingLocationScheduled(event.getUid(), today);
        });
    }

    @Scheduled(fixedRate = 86400000)
    public void cacheMunicipalitiesForUsersWithLocation(){
        log.info("Inside Scheduled Geo Calculations ---------------------------------------->>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        log.info("Caching municipalities for users with location");

        locationInfoBroker.loadUsersWithLocationNotNUll();
    }

}
