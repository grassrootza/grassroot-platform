package za.org.grassroot.services;

import za.org.grassroot.core.dto.MaskedEventDTO;
import za.org.grassroot.core.dto.MaskedGroupDTO;
import za.org.grassroot.core.dto.MaskedUserDTO;
import za.org.grassroot.core.enums.EventType;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Created by luke on 2016/02/04.
 */
public interface AnalyticalService {

     /*
    Methods to return masked user entities for system analysis
     */

    MaskedUserDTO loadMaskedUser(Long userId);

    List<MaskedUserDTO> loadAllUsersMasked();

    Long countAllUsers();

    List<MaskedUserDTO> loadSubsetUsersMasked(List<Long> ids);

    List<MaskedUserDTO> searchByInputNumberOrDisplayName(String inputNumber);

    List<MaskedUserDTO> loadUsersCreatedInInterval(LocalDateTime start, LocalDateTime end);

    int countUsersCreatedInInterval(LocalDateTime start, LocalDateTime end);

    List<MaskedUserDTO> loadUsersCreatedAndInitiatedSessionInPeriod(LocalDateTime start, LocalDateTime end);

    int countUsersThatHaveInitiatedSession();

    int countUsersCreatedAndInitiatedInPeriod(LocalDateTime start, LocalDateTime end);

    List<MaskedUserDTO> loadUsersCreatedInPeriodWithWebProfile(LocalDateTime start, LocalDateTime end);

    int countUsersThatHaveWebProfile();

    int countUsersCreatedWithWebProfileInPeriod(LocalDateTime start, LocalDateTime end);

    // List<MaskedUserDTO> loadUsersInitiatedSessionInPeriod(); // need a UserLog first

    // int countUsersInitiatedSessionInPeriod(); // need a UserLog before we can do this

    // int countUsersCreatedWebProfileInPeriod(); // as above

    /*
    Methods to return groups
     */

    MaskedGroupDTO loadMaskedGroup(Long groupId);

    Long countActiveGroups();

    // List<MaskedGroupDTO> loadAllGroupsMasked();

     //List<MaskedGroupDTO> loadSubsetGroupsMasked(List<Long> ids);

    List<MaskedGroupDTO> loadGroupsCreatedInInterval(LocalDateTime start, LocalDateTime end);

    int countGroupsCreatedInInterval(LocalDateTime start, LocalDateTime end);

    // List<MaskedGroupDTO> loadMaskedGroupsByEventCount(int countStart, int countEnd, EventType eventType);

    // List<MaskedGroupDTO> loadMaskedGroupsByLogBookCount(int countStart, int countEnd);

    // List<MaskedGroupDTO> loadMaskedGroupByCombinedCount(int countStart, int countEnd);

    // int countUnnamedButActiveGroups();

    // List<MaskedGroupDTO> loadMaskedGroupsDeFactoInactive();

    // int countGroupsDeFactoInactive();

    /*
    Methods to analyze patterns in events, including RSVP totals
     */

    MaskedEventDTO loadMaskedEvent(Long eventId);

    Long countAllEvents(EventType eventType);

    // List<MaskedEventDTO> loadAllEventsMasked();

    // List<MaskedEventDTO> loadSubsetEventsMasked(List<Long> ids);

    List<MaskedEventDTO> loadEventsCreatedInInterval(LocalDateTime start, LocalDateTime end, EventType eventType);

    int countEventsCreatedInInterval(LocalDateTime start, LocalDateTime end, EventType eventType);

    /*
    Methods to analyze LogBook entries (to add masks)
     */

    Long countAllLogBooks();

    Long countLogBooksRecordedInInterval(LocalDateTime start, LocalDateTime end);

}
