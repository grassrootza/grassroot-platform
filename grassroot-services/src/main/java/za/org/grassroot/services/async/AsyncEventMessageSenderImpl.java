package za.org.grassroot.services.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.repository.AccountLogRepository;
import za.org.grassroot.core.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AsyncEventMessageSenderImpl implements AsyncEventMessageSender {
    private final Logger logger = LoggerFactory.getLogger(AsyncEventMessageSenderImpl.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountLogRepository accountLogRepository;

    @Autowired
    private GenericJmsTemplateProducerService jmsTemplateProducerService;

    @Override
    @Transactional
    @Async
    public void sendFreeFormMessage(String sendingUserUid, String groupUid, String message) {
        // todo: move most of this to AccountManager
        // for now, just let the notification async handle the group loading etc., here just check the user
        // has permission (is account admin--later, account admin and it's a paid group, with enough credit

        User user = userRepository.findOneByUid(sendingUserUid);
        Account account = user.getAccountAdministered();

        Set<String> standardRoleNames = user.getStandardRoles().stream().map(s -> s.getName()).collect(Collectors.toSet());
        logger.info("User's standard roles: " + standardRoleNames);
        if (account == null || !standardRoleNames.contains(BaseRoles.ROLE_ACCOUNT_ADMIN)) {
            throw new AccessDeniedException("User not account admin!");
        }
        Map<String, String> messageMap = new HashMap<>();
        messageMap.put("group-uid", groupUid);
        messageMap.put("message", message);
        jmsTemplateProducerService.sendWithNoReply("free-form", messageMap);
        logger.info("Queued to free-form... for group" + groupUid + ", with message: " + message);
        accountLogRepository.save(new AccountLog(sendingUserUid, account, AccountLogType.MESSAGE_SENT, groupUid, null,
                                                 "Sent free form message"));
    }

}
