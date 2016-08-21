package za.org.grassroot.services;

import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.dto.KeywordDTO;
import za.org.grassroot.core.dto.MaskedUserDTO;
import za.org.grassroot.core.enums.EventType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2016/02/04.
 * major todo: annotate all of this with system admin
 */
public interface AdminService {

     /*
    Methods to return masked user entities for system analysis
     */

    Long countAllUsers();

    List<MaskedUserDTO> searchByInputNumberOrDisplayName(String inputNumber);

    int countUsersCreatedInInterval(LocalDateTime start, LocalDateTime end);

    int countUsersThatHaveInitiatedSession();

    int countUsersCreatedAndInitiatedInPeriod(LocalDateTime start, LocalDateTime end);

    int countUsersThatHaveWebProfile();

    int countUsersCreatedWithWebProfileInPeriod(LocalDateTime start, LocalDateTime end);

    int countUsersWithGeoLocationData();

    int countGroupsWithGeoLocationData();

    /*
    Methods to return groups
     */

    Long countActiveGroups();

    int countGroupsCreatedInInterval(LocalDateTime start, LocalDateTime end);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void deactiveGroup(String adminUserUid, String groupUid);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void addMemberToGroup(String adminUserUid, String groupUid, MembershipInfo membershipInfo);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void removeMemberFromGroup(String adminUserUid, String groupUid, String memberMsisdn);

    /*
    Methods to analyze patterns in events, including RSVP totals
     */

    Long countAllEvents(EventType eventType);

    int countEventsCreatedInInterval(LocalDateTime start, LocalDateTime end, EventType eventType);

    /*
    Methods to analyze Todo entries (to add masks)
     */

    Long countAllLogBooks();

    Long countLogBooksRecordedInInterval(LocalDateTime start, LocalDateTime end);

    List<KeywordDTO> getKeywordStats();

    /*
    Methods for closer analysis of user sessions etc
     */

    long getMaxSessionsInLastMonth();

    Map<Integer, Integer> getSessionHistogram(Instant start, Instant end, int interval);

}
