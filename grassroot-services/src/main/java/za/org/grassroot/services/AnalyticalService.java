package za.org.grassroot.services;

import za.org.grassroot.core.enums.EventType;

import java.time.LocalDateTime;

/**
 * Created by luke on 2016/12/12.
 */
public interface AnalyticalService {

    Long countAllUsers();

    int countUsersCreatedInInterval(LocalDateTime start, LocalDateTime end);

    int countUsersThatHaveInitiatedSession();

    int countUsersCreatedAndInitiatedInPeriod(LocalDateTime start, LocalDateTime end);

    int countUsersThatHaveWebProfile();

    int countUsersWithGeoLocationData();

    int countGroupsWithGeoLocationData();

    int countUsersThatHaveAndroidProfile();

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

}
