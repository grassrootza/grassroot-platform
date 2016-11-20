package za.org.grassroot.integration.mqtt;

import org.springframework.scheduling.annotation.Async;
import za.org.grassroot.core.domain.Group;

/**
 * Created by luke on 2016/11/05.
 */
public interface MqttSubscriptionService {

    @Async
    void subscribeServerToAllGroupTopics();

    @Async
    void subscribeServerToGroupTopic(Group group);
}
