package za.org.grassroot.integration.mqtt;

import org.jivesoftware.smack.packet.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.integration.utils.MessageUtils;
import za.org.grassroot.integration.xmpp.GcmXmppMessageCodec;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Created by luke on 2016/11/05.
 */
@Service
@ConditionalOnProperty(name = "mqtt.connection.enabled", havingValue = "true",  matchIfMissing = false)
public class MqttSubscriptionServiceImpl implements MqttSubscriptionService {

    @Value("${gcm.topics.path}")
    private String TOPICS;

    private GroupRepository groupRepository;
    private MqttPahoMessageDrivenChannelAdapter mqttAdapter;
    private MessageChannel gcmXmppOutboundChannel;

    @Autowired
    public MqttSubscriptionServiceImpl(GroupRepository groupRepository, MqttPahoMessageDrivenChannelAdapter mqttAdapter, MessageChannel gcmXmppOutboundChannel) {
        this.groupRepository = groupRepository;
        this.mqttAdapter = mqttAdapter;
        this.gcmXmppOutboundChannel = gcmXmppOutboundChannel;
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

    @Async
    @Override
    public void subscribeServerToGroupTopic(Group group) {
        Objects.requireNonNull(group);

        List<String> topicsSubscribeTo = Arrays.asList(mqttAdapter.getTopic());
        if (!topicsSubscribeTo.contains(group.getUid())) {
            mqttAdapter.addTopic(group.getUid(), 1);
        }
    }

    private void pingUsersForGroupChat(Group group) {
        Map<String, Object> data = MessageUtils.generatePingMessageData(group);
        org.springframework.messaging.Message<Message> gcmMessage = GcmXmppMessageCodec.encode(TOPICS.concat(group.getUid()), (String) data.get("messageId"),
                null,
                data);
        gcmXmppOutboundChannel.send(gcmMessage);
    }


}
