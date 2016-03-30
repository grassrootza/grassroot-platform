package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MaskedUserDTO;
import za.org.grassroot.core.enums.EventType;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * Created by luke on 2016/02/04.
 * major todo: annotate all of this with system admin
 */
public interface AnalyticalService {

     /*
    Methods to return masked user entities for system analysis
     */

    Long countAllUsers();

    List<MaskedUserDTO> loadSubsetUsersMasked(List<Long> ids);

    List<MaskedUserDTO> searchByInputNumberOrDisplayName(String inputNumber);

    int countUsersCreatedInInterval(LocalDateTime start, LocalDateTime end);

    int countUsersThatHaveInitiatedSession();

    int countUsersCreatedAndInitiatedInPeriod(LocalDateTime start, LocalDateTime end);

    int countUsersThatHaveWebProfile();

    int countUsersCreatedWithWebProfileInPeriod(LocalDateTime start, LocalDateTime end);

    // int countUsersInitiatedSessionInPeriod(); // need a UserLog before we can do this

    // int countUsersCreatedWebProfileInPeriod(); // as above

    /*
    Methods to return groups
     */

    Long countActiveGroups();

    int countGroupsCreatedInInterval(LocalDateTime start, LocalDateTime end);

    List<Group> getAllGroups();

    Page<Group> getAllActiveGroupsPaginated(Integer pageNumber, Integer pageSize);

    List<Group> getGroupsFiltered(User createdByUser, Integer minGroupSize, Date createdAfterDate, Date createdBeforeDate);


    /*
    Methods to analyze patterns in events, including RSVP totals
     */

    Long countAllEvents(EventType eventType);

    int countEventsCreatedInInterval(LocalDateTime start, LocalDateTime end, EventType eventType);

    /*
    Methods to analyze LogBook entries (to add masks)
     */

    Long countAllLogBooks();

    Long countLogBooksRecordedInInterval(LocalDateTime start, LocalDateTime end);

}
