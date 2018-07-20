package za.org.grassroot.services.account;

import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.broadcast.Broadcast;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Created by luke on 2016/10/25.
 */
public interface AccountFeaturesBroker {

    int numberMembersLeftForGroup(String groupUid);

    int numberTodosLeftForGroup(String groupUid);

    int numberEventsLeftForGroup(String groupUid);

    int numberEventsLeftForParent(String eventUid);

    /**
     * Methods for doing and handling group welcome methods
     */
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void createGroupWelcomeMessages(String userUid, String accountUid, String groupUid, List<String> messages,
                                    Duration delayToSend, Locale language, boolean onlyViaFreeChannels);

    void updateGroupWelcomeNotifications(String userUid, String groupUid, List<String> messages, Duration delayToSend);

    void deactivateGroupWelcomes(String userUid, String groupUid);

    boolean hasGroupWelcomeMessages(String groupUid);

    Broadcast loadWelcomeMessage(String groupUid);

    void generateGroupWelcomeNotifications(String addingUserUid, String groupUid, Set<String> addedMemberUids);

    String generateGroupWelcomeReply(String userUid, String groupUid);

}
