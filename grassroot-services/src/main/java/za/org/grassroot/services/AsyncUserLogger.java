package za.org.grassroot.services;

import za.org.grassroot.core.enums.UserLogType;

import java.util.Set;

/**
 * Created by luke on 2016/02/22.
 */
public interface AsyncUserLogger {

    void recordUserLog(String userUid, UserLogType userLogType, String description);

    void logUserCreation(Set<String> userUids, String description);

}
