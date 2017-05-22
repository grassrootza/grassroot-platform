package za.org.grassroot.services;

import za.org.grassroot.core.dto.KeywordDTO;
import za.org.grassroot.core.enums.EventType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2016/12/12.
 */
public interface AnalyticalService {

    Long countAllUsers();

    int countUsersCreatedInInterval(LocalDateTime start, LocalDateTime end);

    int countUsersThatHaveInitiatedSession();

    int countUsersCreatedAndInitiatedInPeriod(LocalDateTime start, LocalDateTime end);

    int countUsersThatHaveWebProfile();

    int countUsersCreatedWithWebProfileInPeriod(LocalDateTime start, LocalDateTime end);

    int countUsersWithGeoLocationData();

    int countGroupsWithGeoLocationData();

    int countUsersThatHaveAndroidProfile();

    int countUsersCreatedWithAndroidProfileInPeriod(LocalDateTime start, LocalDateTime end);

    Long countActiveGroups();

    int countGroupsCreatedInInterval(LocalDateTime start, LocalDateTime end);

        /*
    Methods to analyze patterns in events, including RSVP totals
     */

    Long countAllEvents(EventType eventType);

    int countEventsCreatedInInterval(LocalDateTime start, LocalDateTime end, EventType eventType);

    /*
    Methods to analyze to-do entries (to add masks)
     */

    Long countAllTodos();
    Long countTodosRecordedInInterval(LocalDateTime start, LocalDateTime end);

    // Count safety events, put in nulls for no start/end point

    int countSafetyEventsInInterval(LocalDateTime start, LocalDateTime end);

    /*
    Methods for closer analysis of user sessions etc
     */
    List<KeywordDTO> getKeywordStats(LocalDateTime from);

    long getMaxSessionsInLastMonth();

    Map<Integer, Integer> getSessionHistogram(Instant start, Instant end, int interval);

    /*
    And now a couple for livewire alerts and notifications
     */
    long countLiveWireAlertsInInterval(Instant start, Instant end);
    long countNotificationsInInterval(Instant start, Instant end);
}
