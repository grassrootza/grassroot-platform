package za.org.grassroot.services;

import za.org.grassroot.core.enums.UserLogType;

/**
 * Created by luke on 2016/02/22.
 */
public interface AsyncUserService {

    public void recordUserLog(Long userId, UserLogType userLogType, String description);

}
