package za.org.grassroot.services.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.core.repository.UserLogRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.util.CacheUtilService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

import static org.springframework.data.jpa.domain.Specification.where;
import static za.org.grassroot.core.enums.UserInterfaceType.UNKNOWN;
import static za.org.grassroot.core.enums.UserInterfaceType.USSD;
import static za.org.grassroot.core.specifications.UserLogSpecifications.*;

/**
 * Created by luke on 2016/02/22.
 */
@Service @Slf4j
public class AsyncUserLoggerImpl implements AsyncUserLogger {

    private final UserRepository userRepository;
    private final UserLogRepository userLogRepository;
    private final CacheUtilService cacheUtilService;
    private final RoleRepository roleRepository;

    @Autowired
    public AsyncUserLoggerImpl(UserRepository userRepository, UserLogRepository userLogRepository, CacheUtilService cacheUtilService, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.userLogRepository = userLogRepository;
        this.cacheUtilService = cacheUtilService;
        this.roleRepository = roleRepository;
    }

    @Async
    @Override
    @Transactional
    public void logUserLogin(String userUid, UserInterfaceType channel) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        if (UserInterfaceType.WEB.equals(channel) || UserInterfaceType.WEB_2.equals(channel)) {
            user.setHasWebProfile(true);
        } else if (UserInterfaceType.ANDROID.equals(channel) || UserInterfaceType.ANDROID_2.equals(channel)) {
            user.setHasAndroidProfile(true);
        }
        if (!user.isHasInitiatedSession()) {
            user.setHasInitiatedSession(true);
            Role fullUserRole = roleRepository.findByNameAndRoleType(BaseRoles.ROLE_FULL_USER, Role.RoleType.STANDARD).get(0);
            user.addStandardRole(fullUserRole);
        }
        userLogRepository.save(new UserLog(userUid, UserLogType.USER_SESSION, "", channel));
    }

    @Async
    @Override
    @Transactional
    public void recordUserLog(String userUid, UserLogType userLogType, String description) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(userLogType);
        UserLog userLog = new UserLog(userUid, userLogType, description, UNKNOWN);
        userLogRepository.saveAndFlush(userLog);
    }

    @Async
    @Override
    @Transactional
    public void storeUserLogs(Set<UserLog> userLogSet) {
        userLogRepository.saveAll(userLogSet);
    }

    @Async
    @Override
    @Transactional
    public void recordUserSession(String userUid, UserInterfaceType interfaceType) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(interfaceType);
        if (!cacheUtilService.checkSessionStatus(userUid, interfaceType)) {
            cacheUtilService.setSessionOpen(userUid, interfaceType);
            userLogRepository.save(new UserLog(userUid, UserLogType.USER_SESSION, "", interfaceType));
        } else {
            log.info("Cache return positive ... not recording a new session");
        }
    }

    @Async
    @Override
    @Transactional
    public void recordUssdMenuAccessed(String userUid, String ussdSection, String ussdMenu) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(ussdSection);
        Objects.requireNonNull(ussdMenu);

        String urlToSave = ussdSection + ":" + ussdMenu;
        userLogRepository.save(new UserLog(userUid, UserLogType.USSD_MENU_ACCESSED, urlToSave, USSD));
    }

    @Async
    @Override
    @Transactional
    public void recordUssdInterruption(String userUid, String savedUrl) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(savedUrl);

        // note: given pattern of USSD calls, this may not catch all (e.g., "set my name")
        userLogRepository.save(new UserLog(userUid, UserLogType.USSD_INTERRUPTED, stripParameters(savedUrl), USSD));
    }

    private String stripParameters(String url) {
        String stripped;
        int paramDelim = url.indexOf("?");
        if (paramDelim == -1)
            stripped = url;
        else
            stripped = url.substring(0, paramDelim);
        log.info("Inside recording the ussd interruption ... parameter-stripped URL is: {}", stripped);
        return stripped;
    }

    @Async
    @Override
    @Transactional
    public void recordUserInputtedDateTime(String userUid, String dateTimeString, String action, UserInterfaceType interfaceType) {

        Objects.requireNonNull(userUid);
        Objects.requireNonNull(dateTimeString);

        String description = ((action != null) ? "action: " + action + ", " : "") + "input: " + dateTimeString;
        log.info("Storing user inputted date-time as ... {}", description);
        userLogRepository.save(new UserLog(userUid, UserLogType.USSD_DATE_ENTERED, description, interfaceType));
    }

    @Override
    @Transactional(readOnly = true)
    public int numberSessions(String userUid, UserInterfaceType interfaceType, Instant start, Instant end) {
        return (int) userLogRepository.count(where(forUser(userUid))
                .and(ofType(UserLogType.USER_SESSION))
                .and(usingInterface(interfaceType))
                .and(creationTimeBetween(start, end)));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSkippedName(String userUid) {
        return (int) userLogRepository.count(where(forUser(userUid))
                .and(ofType(UserLogType.USER_SKIPPED_NAME))
                .and(hasDescription(""))) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSkippedNamingGroup(String userUid, String groupUid) {
        return userLogRepository.count(where(forUser(userUid))
                .and(ofType(UserLogType.USER_SKIPPED_NAME))
                .and(hasDescription(groupUid))) > 0;
    }

    @Override
    public boolean hasUsedJoinCodeRecently(String userUid) {
        Instant end = Instant.now();
        Instant start = end.minus(5, ChronoUnit.MINUTES);
        return userLogRepository.count(where(forUser(userUid))
                .and(ofType(UserLogType.USED_A_JOIN_CODE))
                .and(creationTimeBetween(start, end))) > 0;
    }

    @Override
    @Transactional
    public void removeAllUserInfoLogs(String userUid) {
        userLogRepository.deleteAllByUserUidAndUserLogTypeIn(userUid,
                Arrays.asList(UserLogType.CHANGED_ADDRESS,
                        UserLogType.CHANGED_LANGUAGE,
                        UserLogType.USER_DETAILS_CHANGED,
                        UserLogType.USER_EMAIL_CHANGED,
                        UserLogType.USER_PHONE_CHANGED,
                        UserLogType.DETAILS_CHANGED_BY_GROUP,
                        UserLogType.DETAILS_CHANGED_ON_JOIN,
                        UserLogType.SENT_UNEXPECTED_SMS_MESSAGE));
    }


}
