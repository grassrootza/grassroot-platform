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

    int countGroupsWithGeoLocationData();

    int countUsersThatHaveAndroidProfile();

    int countUsersThatHaveUsedWhatsApp();

    int countUsersWithWhatsAppOptIn();

    Long countActiveGroups();

    int countGroupsCreatedInInterval(LocalDateTime start, LocalDateTime end);

    Long countAllEvents(EventType eventType);

    int countEventsCreatedInInterval(LocalDateTime start, LocalDateTime end, EventType eventType);

    Long countAllTodos();

    Long countTodosRecordedInInterval(LocalDateTime start, LocalDateTime end);

    // Count safety events, put in nulls for no start/end point

    int countSafetyEventsInInterval(LocalDateTime start, LocalDateTime end);

}
