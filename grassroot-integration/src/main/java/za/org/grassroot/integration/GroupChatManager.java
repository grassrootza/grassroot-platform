package za.org.grassroot.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupChatMessageStats;
import za.org.grassroot.core.domain.GroupChatSettings;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupChatMessageStatsRepository;
import za.org.grassroot.core.repository.GroupChatSettingsRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.UIDGenerator;
import za.org.grassroot.integration.exception.GroupChatSettingNotFoundException;
import za.org.grassroot.integration.mqtt.MQTTPayload;
import za.org.grassroot.integration.mqtt.MqttObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Created by paballo on 2016/09/08.
 */
@Service
@ConditionalOnProperty(name = "mqtt.connection.enabled", havingValue = "true",  matchIfMissing = false)
public class GroupChatManager implements GroupChatService {

    private static final Logger logger = LoggerFactory.getLogger(GroupChatManager.class);
    private static final String GRASSROOT_SYSTEM = "Grassroot";

    @Value("${gcm.topics.path}")
    private String TOPICS;

    @Value("${mqtt.status.read.threshold:0.5}")
    private Double readStatusThreshold;

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupChatSettingsRepository groupChatSettingsRepository;
    private final MessageChannel mqttOutboundChannel;
    private final GroupChatMessageStatsRepository groupChatMessageStatsRepository;
    private final MessageSourceAccessor messageSourceAccessor;

    @Autowired
    public GroupChatManager(UserRepository userRepository, GroupRepository groupRepository, GroupChatSettingsRepository groupChatSettingsRepository,
                            MessageChannel mqttOutboundChannel,
                            GroupChatMessageStatsRepository groupChatMessageStatsRepository, GcmRegistrationBroker gcmRegistrationBroker, MqttObjectMapper payloadMapper,
                            @Qualifier("integrationMessageSourceAccessor") MessageSourceAccessor messageSourceAccessor) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupChatSettingsRepository = groupChatSettingsRepository;
        this.mqttOutboundChannel = mqttOutboundChannel;
        this.groupChatMessageStatsRepository = groupChatMessageStatsRepository;
        this.messageSourceAccessor = messageSourceAccessor;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "groupChatSettings", key = "userUid + '_'+ groupUid")
    public GroupChatSettings load(String userUid, String groupUid) throws GroupChatSettingNotFoundException {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = groupChatSettingsRepository.findByUserAndGroup(user, group);

        if (groupChatSettings == null) {
            throw new GroupChatSettingNotFoundException("Group chat setting not found found for user with uid " + userUid);
        }

        return groupChatSettings;
    }

    @Override
    @Async
    @Transactional
    public void markMessagesAsRead(String groupUid, String groupName, Set<String> messageUids) {
        for (String messageUid : messageUids) {
            MQTTPayload payload = new MQTTPayload(messageUid, groupUid, groupName, GRASSROOT_SYSTEM, "update_read_status");
            GroupChatMessageStats groupChatMessageStats = groupChatMessageStatsRepository.findByUidAndRead(messageUid, false);
            if (groupChatMessageStats != null) {
                groupChatMessageStats.incrementReadCount();
                User user = groupChatMessageStats.getUser();
                if (groupChatMessageStats.getTimesRead() / groupChatMessageStats.getIntendedReceipients() > readStatusThreshold) {
                    groupChatMessageStats.setRead(true);
                    try {
                        final ObjectMapper mapper = new ObjectMapper();
                        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
                        final String message = mapper.writeValueAsString(payload);
                        mqttOutboundChannel.send(MessageBuilder.withPayload(message).
                                setHeader(MqttHeaders.TOPIC, user.getPhoneNumber()).build());
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                }

            }
        }
    }

    @Override
    @Transactional
    public void updateActivityStatus(String userUid, String groupUid, boolean active, boolean userInitiated) throws GroupChatSettingNotFoundException {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = groupChatSettingsRepository.findByUserAndGroup(user, group);
        if (null == groupChatSettings) {
            throw new GroupChatSettingNotFoundException("Message settings not found for user with uid " + userUid);
        }
        groupChatSettings.setActive(active);
        groupChatSettings.setUserInitiated(userInitiated);
        groupChatSettings.setCanSend(active);
        if (userInitiated) groupChatSettings.setCanReceive(active);
        if(!userInitiated && !active){
            final MQTTPayload payload = generateUserMutedResponseData(group);
            final String message;
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,true);
                message = mapper.writeValueAsString(payload);
                mqttOutboundChannel.send(MessageBuilder.withPayload(message).
                        setHeader(MqttHeaders.TOPIC, user.getPhoneNumber()).build());
            } catch (JsonProcessingException e) {
                logger.debug("Error parsing message");
            }

        }
        if (userInitiated) {
            groupChatSettings.setCanReceive(active);
        }
        groupChatSettingsRepository.save(groupChatSettings);

    }

    @Override
    @Transactional(readOnly = true)
    public List<String> usersMutedInGroup(String groupUid) {
        Objects.requireNonNull(groupUid);
        Group group = groupRepository.findOneByUid(groupUid);
        List<GroupChatSettings> groupChatSettingses = groupChatSettingsRepository.findByGroupAndActiveAndCanSend(group, true, false);
        List<String> mutedUsersUids = new ArrayList<>();
        for (GroupChatSettings groupChatSettings : groupChatSettingses) {
            User user = groupChatSettings.getUser();
            mutedUsersUids.add(user.getUsername());
        }
        return mutedUsersUids;
    }


    private MQTTPayload generateUserMutedResponseData(Group group) {
        String groupUid = group.getUid();
        String messageId = UIDGenerator.generateId();
        String responseMessage = messageSourceAccessor.getMessage("gcm.xmpp.chat.muted");
        MQTTPayload payload =  new MQTTPayload(messageId,
                groupUid,
                group.getGroupName(),
                group.getGroupName(),
                "normal");
        payload.setText(responseMessage);

        return payload;
    }

}
