package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.UserLogRepository;

/**
 * Created by luke on 2016/02/22.
 */
@Service
@Transactional
@Lazy
public class AsyncUserLoggerImpl implements AsyncUserLogger {

    @Autowired
    UserLogRepository userLogRepository;

    @Async
    @Override
    public void recordUserLog(Long userId, UserLogType userLogType, String description) {
        UserLog userLog = new UserLog(userId, userLogType, description);
        userLogRepository.saveAndFlush(userLog);
    }
}
