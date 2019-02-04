package za.org.grassroot.services.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.StandardRole;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.UserLocationLog;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.UserLocationLogRepository;
import za.org.grassroot.core.repository.UserLogRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.util.CacheUtilService;

import java.time.Instant;
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
    private final UserLocationLogRepository userLocationLogRepository;
    private final CacheUtilService cacheUtilService;

    @Autowired
    public AsyncUserLoggerImpl(UserRepository userRepository, UserLogRepository userLogRepository, UserLocationLogRepository userLocationLogRepository, CacheUtilService cacheUtilService) {
        this.userRepository = userRepository;
        this.userLogRepository = userLogRepository;
        this.userLocationLogRepository = userLocationLogRepository;
        this.cacheUtilService = cacheUtilService;
    }

    @Async
    @Override
    @Transactional
    public void logUserLogin(String userUid, UserInterfaceType channel) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);

        log.info("Recording user login (should be off main thread), user ID : {}, channel : {}", userUid, channel);

        // note: WhatsApp approval requires something different, so do _not_ set it here (because will be recorded based on 'within band' WhatsApp use)
        if (UserInterfaceType.WEB.equals(channel) || UserInterfaceType.WEB_2.equals(channel)) {
            user.setHasWebProfile(true);
        } else if (UserInterfaceType.ANDROID.equals(channel) || UserInterfaceType.ANDROID_2.equals(channel)) {
            user.setHasAndroidProfile(true);
        }

        if (!user.isHasInitiatedSession()) {
            user.setHasInitiatedSession(true);
            user.addStandardRole(StandardRole.ROLE_FULL_USER);
        }

        userLogRepository.save(new UserLog(userUid, UserLogType.USER_SESSION, "", channel));
    }

    @Async
    @Override
    @Transactional
    public void recordUserLog(String userUid, UserLogType userLogType, String description, UserInterfaceType channel) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(userLogType);
        UserLog userLog = new UserLog(userUid, userLogType, description,
                channel == null ? UNKNOWN : channel);
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
            log.debug("New session for user, recording in logs ... should be off main thread. User ID: {}, channel: {}", userUid, interfaceType);
            cacheUtilService.setSessionOpen(userUid, interfaceType);
            userLogRepository.save(new UserLog(userUid, UserLogType.USER_SESSION, "", interfaceType));
        } else {
            log.info("Cache return positive ... not recording a new session");
        }
    }

    @Override
    @Transactional
    public void recordUserLocation(String userUid, GeoLocation location, LocationSource locationSource, UserInterfaceType channel) {
        log.info("Recording user's location from explicit pin send");
        UserLocationLog locationLog = new UserLocationLog(Instant.now(), userUid, location, locationSource);
        userLocationLogRepository.save(locationLog);
        log.info("Completed log recording");
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
        Specification<UserLog> specs = where(forUser(userUid)).and(ofType(UserLogType.USER_SESSION));
        if (interfaceType != null)
            specs = specs.and(usingInterface(interfaceType));
        if (start != null && end != null)
            specs = specs.and(creationTimeBetween(start, end));

        return (int) userLogRepository.count(specs);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSkippedName(String userUid) {
        return (int) userLogRepository.count(where(forUser(userUid))
                .and(ofType(UserLogType.USER_SKIPPED_NAME))) > 0;
    }

    @Override
    public boolean hasSkippedProvince(String userUid) {
        return (int) userLogRepository.count(where(forUser(userUid))
                .and(ofType(UserLogType.USER_SKIPPED_PROVINCE))) > 0;
    }

    @Override
    public boolean hasChangedLanguage(String userUid) {
        return userLogRepository.count(where(forUser(userUid))
                .and(ofType(UserLogType.CHANGED_LANGUAGE))) > 0;
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
