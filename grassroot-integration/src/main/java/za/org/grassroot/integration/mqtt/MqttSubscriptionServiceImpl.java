package za.org.grassroot.integration.mqtt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;

import java.util.Arrays;
import java.util.List;

/**
 * Created by luke on 2016/11/05.
 */
@Service
@ConditionalOnProperty(name = "mqtt.connection.enabled", havingValue = "true",  matchIfMissing = false)
public class MqttSubscriptionServiceImpl implements MqttSubscriptionService {

    private GroupRepository groupRepository;
    private MqttPahoMessageDrivenChannelAdapter mqttAdapter;

    @Autowired
    public MqttSubscriptionServiceImpl(GroupRepository groupRepository, MqttPahoMessageDrivenChannelAdapter mqttAdapter) {
        this.groupRepository = groupRepository;
        this.mqttAdapter = mqttAdapter;
    }

    @Override @Async
    @Transactional
    public void subscribeServerToAllGroupTopics() {
        List<Group> groups = groupRepository.findAll();
        List<String> topicsSubscribedTo = Arrays.asList(mqttAdapter.getTopic());
        for(Group group: groups){
            if(!topicsSubscribedTo.contains(group.getUid())){
                mqttAdapter.addTopic(group.getUid(), 1);
            }
        }
    }

    @Override @Async
    @Transactional
    public void subscribeServerToUserTopic(User user){
        List<String> topicsSubscribeTo = Arrays.asList(mqttAdapter.getTopic());
        if(!topicsSubscribeTo.contains(user.getPhoneNumber())){
            mqttAdapter.addTopic(user.getPhoneNumber(), 1);
        }
    }


}
