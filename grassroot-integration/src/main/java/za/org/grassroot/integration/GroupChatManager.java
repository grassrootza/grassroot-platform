package za.org.grassroot.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.jivesoftware.smack.packet.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.support.json.Jackson2JsonMessageParser;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.SerializationUtils;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupChatMessageStats;
import za.org.grassroot.core.domain.GroupChatSettings;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.repository.GroupChatMessageStatsRepository;
import za.org.grassroot.core.repository.GroupChatSettingsRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.UIDGenerator;
import za.org.grassroot.integration.domain.AndroidClickActionType;
import za.org.grassroot.integration.domain.GroupChatMessage;
import za.org.grassroot.integration.domain.MQTTPayload;
import za.org.grassroot.integration.domain.RelayedChatMessage;
import za.org.grassroot.integration.exception.GroupChatSettingNotFoundException;
import za.org.grassroot.integration.exception.SeloParseDateTimeFailure;
import za.org.grassroot.integration.utils.Constants;
import za.org.grassroot.integration.utils.MessageUtils;
import za.org.grassroot.integration.xmpp.GcmXmppMessageCodec;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Created by paballo on 2016/09/08.
 */
@Service
public class GroupChatManager implements GroupChatService {

    private static final Logger logger = LoggerFactory.getLogger(GroupChatManager.class);

    // todo : externalize property, of course
    private static final DateTimeFormatter cmdMessageFormat = DateTimeFormatter.ofPattern("HH:mm, EEE d MMM");
    private static final DateTimeFormatter cmdMessageSystemFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Value("${gcm.topics.path}")
    private String TOPICS;

    @Value("${mqtt.status.read.threshold}")
    private Double readStatusThreshold;



    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupChatSettingsRepository groupChatSettingsRepository;

    @Autowired
    private LearningService learningService;

    @Autowired
    private MessageChannel gcmXmppOutboundChannel;

    @Autowired
    private MessageChannel mqttOutboundChannel;

    @Autowired
    private MqttPahoMessageDrivenChannelAdapter mqttAdapter;

    @Autowired
    private GroupChatMessageStatsRepository groupChatMessageStatsRepository;


    @Autowired
    @Qualifier("integrationMessageSourceAccessor")
    MessageSourceAccessor messageSourceAccessor;

    @Override
    @Transactional
    public void createUserGroupMessagingSetting(String userUid, String groupUid, boolean active, boolean canSend, boolean canReceive) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);


        GroupChatSettings groupChatSettings = new GroupChatSettings(user, group, active, true, true, true);
        groupChatSettingsRepository.save(groupChatSettings);
    }

    @Override
    @Transactional(readOnly = true)
    public void relayChatMessage(String userPhoneNumber, String groupUid, String message, String localMsgUid, String userGcmKey) {
        User user = userRepository.findByPhoneNumber(userPhoneNumber);
        GroupChatSettings settings = load(user.getUid(), groupUid);

        GroupChatMessage msg = new RelayedChatMessage.ChatMessageBuilder("org.grassroot.android")
                .from(userGcmKey)
                .to(groupUid)
                .text(message)
                .messageUid(localMsgUid)
                .senderPhone(userPhoneNumber)
                .build();

        if (settings != null) {
            Group group = settings.getGroup();
            org.springframework.messaging.Message<Message> outboundMessage = settings.isCanSend() ?
                    generateMessage(user, msg, group) : generateCannotSendMessage(msg, group);
            gcmXmppOutboundChannel.send(outboundMessage);

        } else {
            throw new GroupChatSettingNotFoundException("User does not have chat settings, cannot be part of group");
        }
    }

    @Override
    public void processAndRouteIncomingChatMessage(GroupChatMessage incoming) {
        String phoneNumber = (String) incoming.getData().get("phoneNumber");
        String groupUid = (String) incoming.getData().get("groupUid");
        User user = userRepository.findByPhoneNumber(phoneNumber);
        GroupChatSettings groupChatSettings = load(user.getUid(), groupUid);
        if (groupChatSettings != null) {
            Group group = groupChatSettings.getGroup();
            org.springframework.messaging.Message<Message> message;
            logger.debug("Posting to topic with id={}", groupUid);
            try {
                if (isCanSend(user.getUid(), groupUid)) {
                    logger.debug("Posting to topic with id={}", groupUid);
                    message = generateMessage(user, incoming, group);
                } else {
                    message = generateCannotSendMessage(incoming, group);
                }
                gcmXmppOutboundChannel.send(message);
            } catch (GroupChatSettingNotFoundException e) {
                logger.debug("User with phoneNumber={} is not enabled to send messages to this group", phoneNumber);
            }
        }
    }

    @Override
    @Async
    public void processCommandMessage(MQTTPayload incoming) {
        String groupUid = incoming.getGroupUid();
        Group group = groupRepository.findOneByUid(groupUid);
        MQTTPayload payload = generateMessage(incoming, group);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            final String message = objectMapper.writeValueAsString(payload);
            logger.debug("Outgoing mqtt payload ={]", payload);
            mqttOutboundChannel.send(MessageBuilder.withPayload(message).
                    setHeader(MqttHeaders.TOPIC, incoming.getPhoneNumber()).build());
        } catch (JsonProcessingException e) {
            logger.debug("Message conversion failed with error ={}", e.getMessage());
        }

    }



    @Override
    @Async
    @Transactional
    public void markMessagesAsRead(String groupUid, String groupName, Set<String> messageUids) {
        for (String messageUid : messageUids) {
            Map<String, Object> data = generateMarkMessageAsReadData(messageUid, groupName, groupName);
            GroupChatMessageStats groupChatMessageStats = groupChatMessageStatsRepository.findByUid(messageUid);
            if(groupChatMessageStats !=null){
            groupChatMessageStats.incrementReadCount();
            if(groupChatMessageStats.getTimesRead()/groupChatMessageStats.getIntendedReceipients()> readStatusThreshold) {
                groupChatMessageStats.setRead(true);
                //todo send message via mqtt
                org.springframework.messaging.Message gcmMessage = GcmXmppMessageCodec.encode(TOPICS.concat(groupUid), (String) data.get("messageId"),
                        null,
                        data);
                gcmXmppOutboundChannel.send(gcmMessage);
            }
            }

        }
    }

    public org.springframework.messaging.Message<Message> generateCannotSendMessage(GroupChatMessage input, Group group) {
        Map<String, Object> data = MessageUtils.generateUserMutedResponseData(messageSourceAccessor, input, group);
        return GcmXmppMessageCodec.encode(input.getFrom(), String.valueOf(data.get("messageId")),
                null, data);
    }

    public org.springframework.messaging.Message<Message> generateMessage(User user, GroupChatMessage input, Group group) {
        org.springframework.messaging.Message<Message> gcmMessage;

        Map<String, Object> data;
        if (!MessageUtils.isCommand((input))) {
            String topic = TOPICS.concat(group.getUid());
            data = generateChatMessageData(input, user, group);
            gcmMessage = GcmXmppMessageCodec.encode(topic, String.valueOf(data.get("messageId")),
                    null, data);
        } else {
            final String msg = String.valueOf(input.getData().get("message"));
            final String[] tokens = MessageUtils.tokenize(msg);
            final TaskType cmdType = msg.contains("/meeting") ? TaskType.MEETING : msg.contains("/vote") ? TaskType.VOTE : TaskType.TODO;

            if (tokens.length < (TaskType.MEETING.equals(cmdType) ? 3 : 2)) {
                data = generateInvalidCommandResponseData(input, group);
            } else {
                try {
                    final LocalDateTime parsedDateTime = learningService.parse(tokens[1]);
                    if (DateTimeUtil.convertToSystemTime(parsedDateTime, DateTimeUtil.getSAST()).isBefore(Instant.now())) {
                        data = generateDateInPastData(input, group);
                    } else {
                        tokens[1] = parsedDateTime.format(cmdMessageFormat);
                        data = generateCommandResponseData(input, group, cmdType, tokens, parsedDateTime);
                    }
                } catch (SeloParseDateTimeFailure e) {
                    data = generateInvalidCommandResponseData(input, group);
                }
            }
            gcmMessage = GcmXmppMessageCodec.encode(input.getFrom(), String.valueOf(data.get("messageId")),
                    null, data);
        }

        return gcmMessage;
    }




    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "groupChatSettings", key = "userUid + '_'+ groupUid")
    public GroupChatSettings load(String userUid, String groupUid) throws GroupChatSettingNotFoundException {
        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = groupChatSettingsRepository.findByUserAndGroup(user, group);

        if (groupChatSettings == null) {
            throw new GroupChatSettingNotFoundException("Group chat setting not found found for user with uid " + userUid);
        }

        return groupChatSettings;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCanSend(String userUid, String groupUid) throws GroupChatSettingNotFoundException {
        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = groupChatSettingsRepository.findByUserAndGroup(user, group);
        if (null == groupChatSettings) {
            throw new GroupChatSettingNotFoundException("Message settings not found for user with uid " + userUid);
        }

        return groupChatSettings.isCanSend();
    }

    @Override
    @Transactional
    public void updateActivityStatus(String userUid, String groupUid, boolean active, boolean userInitiated) throws GroupChatSettingNotFoundException {
        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = groupChatSettingsRepository.findByUserAndGroup(user, group);
        if (null == groupChatSettings) {
            throw new GroupChatSettingNotFoundException("Message settings not found for user with uid " + userUid);
        }
        groupChatSettings.setActive(active);
        groupChatSettings.setUserInitiated(userInitiated);
        groupChatSettings.setCanSend(active);
        if (userInitiated) {
            groupChatSettings.setCanReceive(active);
        }
        groupChatSettingsRepository.save(groupChatSettings);

    }


    @Override @Async
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
    public void subscribeServerToUserTopic(User user){
        List<String> topicsSubscribeTo = Arrays.asList(mqttAdapter.getTopic());
        if(!topicsSubscribeTo.contains(user.getPhoneNumber())){
            mqttAdapter.addTopic(user.getPhoneNumber(), 1);
        }
    }

    @Override
    @Transactional
    @Async
    public void createGroupChatMessageStats(MQTTPayload payload) {
        Group group = groupRepository.findOneByUid(payload.getGroupUid());
        Long numberOfIntendedRecepients =
                groupChatSettingsRepository.countByGroupAndActive(group,true);

        GroupChatMessageStats groupChatMessageStats = new GroupChatMessageStats(payload.getUid(),group,numberOfIntendedRecepients,1L, false);
        groupChatMessageStatsRepository.save(groupChatMessageStats);

    }

    @Override
    @Transactional(readOnly = true)
    public boolean messengerSettingExist(String userUid, String groupUid) {

        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = groupChatSettingsRepository.findByUserAndGroup(user, group);
        return (groupChatSettings != null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupChatSettings> loadUsersToBeUnmuted() {
        return groupChatSettingsRepository.findByActiveAndUserInitiatedAndReactivationTimeBefore(false, false, Instant.now());

    }

    @Override
    public List<String> usersMutedInGroup(String groupUid) {
        Objects.nonNull(groupUid);
        Group group = groupRepository.findOneByUid(groupUid);
        List<GroupChatSettings> groupChatSettingses = groupChatSettingsRepository.findByGroupAndActiveAndCanSend(group, true, false);
        List<String> mutedUsersUids = new ArrayList<>();
        for (GroupChatSettings groupChatSettings : groupChatSettingses) {
            User user = groupChatSettings.getUser();
            mutedUsersUids.add(user.getUsername());
        }
        return mutedUsersUids;
    }

    private Map<String, Object> generateChatMessageData(GroupChatMessage input, User user, Group group) {
        String message = (String) input.getData().get("message");
        Map<String, Object> data = MessageUtils.prePopWithGroupData(group);
        String messageId = UIDGenerator.generateId().concat(String.valueOf(System.currentTimeMillis()));
        data.put(Constants.BODY, message);
        data.put("messageId", messageId);
        data.put("messageUid", input.getMessageUid());
        data.put(Constants.TITLE, user.nameToDisplay());
        data.put("type", "normal");
        data.put("phone_number", user.getPhoneNumber());
        data.put("userUid", user.getUid());
        data.put(Constants.ENTITY_TYPE, AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("click_action", AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("time", input.getData().get("time"));
        return data;
    }


    private Map<String, Object> generateMarkMessageAsReadData(String messageUid, String groupUid, String groupName) {

        Map<String, Object> data = new HashMap<>();
        String messageId = UIDGenerator.generateId().concat(String.valueOf(System.currentTimeMillis()));
        data.put(Constants.GROUP_UID, groupUid);
        data.put(Constants.GROUP_NAME, groupName);
        data.put("messageId", messageId);
        data.put("messageUid", messageUid);
        data.put("type", "update_read_status");
        data.put(Constants.ENTITY_TYPE, AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("click_action", AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("time", Instant.now());

        return data;
    }

    private Map<String, Object> generateCommandResponseData(GroupChatMessage input, Group group, TaskType type, String[] tokens, LocalDateTime taskDateTime) {
        final String messageId = UIDGenerator.generateId().concat(String.valueOf(System.currentTimeMillis()));
        Map<String, Object> data = MessageUtils.prePopWithGroupData(group);

        data.put("messageId", messageId);
        data.put("messageUid", input.getMessageUid());
        data.put(Constants.TITLE, "Grassroot");

        if (TaskType.MEETING.equals(type)) {
            final String text = messageSourceAccessor.getMessage("gcm.xmpp.command.meeting", tokens);
            data.put("type", TaskType.MEETING.toString());
            data.put(Constants.BODY, text);
        } else if (TaskType.VOTE.equals(type)) {
            final String text = messageSourceAccessor.getMessage("gcm.xmpp.command.vote", tokens);
            data.put("type", TaskType.VOTE.toString());
            data.put(Constants.BODY, text);
        } else {
            final String text = messageSourceAccessor.getMessage("gcm.xmpp.command.todo", tokens);
            data.put("type", TaskType.TODO.toString());
            data.put(Constants.BODY, text);
        }

        data.put("tokens", Arrays.asList(tokens));
        data.put(Constants.ENTITY_TYPE, AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("click_action", AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("time", input.getData().get("time"));

        if (taskDateTime != null) {
            data.put("task_date_time", taskDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        return data;
    }

    // todo : switch a lot of these field names to constants / enums
    private Map<String, Object> generateInvalidCommandResponseData(GroupChatMessage input, Group group) {
        String messageId = UIDGenerator.generateId().concat(String.valueOf(System.currentTimeMillis()));
        String responseMessage = messageSourceAccessor.getMessage("gcm.xmpp.command.invalid");
        Map<String, Object> data = MessageUtils.prePopWithGroupData(group);
        data.put("messageId", messageId);
        data.put("messageUid", input.getMessageUid());
        data.put(Constants.TITLE, "Grassroot");
        data.put(Constants.BODY, responseMessage);
        data.put(Constants.ENTITY_TYPE, AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("click_action", AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("type", "error");
        data.put("time", input.getData().get("time"));
        return data;
    }

    private Map<String, Object> generateDateInPastData(GroupChatMessage input, Group group) {
        Map<String, Object> data = MessageUtils.prePopWithGroupData(group);
        data.put("messageId", UIDGenerator.generateId());
        data.put("messageUid", input.getMessageUid());
        data.put(Constants.TITLE, "Grassroot");
        data.put(Constants.BODY, messageSourceAccessor.getMessage("gcm.xmpp.command.timepast"));
        data.put(Constants.ENTITY_TYPE, AndroidClickActionType.CHAT_MESSAGE);
        data.put("click_action", AndroidClickActionType.CHAT_MESSAGE);
        data.put("type", "error");
        data.put("time", input.getData().get("time"));
        return data;
    }


    private MQTTPayload generateInvalidCommandResponseData(MQTTPayload input, Group group) {

        String responseMessage = messageSourceAccessor.getMessage("gcm.xmpp.command.invalid");
        MQTTPayload outboundMessage = new MQTTPayload(input.getUid(), input.getGroupUid(),
                group.getGroupName(),
                "Grassroot", input.getTime(),"error");
        outboundMessage.setText(responseMessage);
        return outboundMessage;
    }


    private MQTTPayload generateDateInPastData(MQTTPayload input, Group group) {

        String responseMessage = messageSourceAccessor.getMessage("gcm.xmpp.command.timepast");
        MQTTPayload outboundMessage = new MQTTPayload(input.getUid(), input.getGroupUid(),
                group.getGroupName(),
                "Grassroot", input.getTime(),"error");
        outboundMessage.setText(responseMessage);
        return outboundMessage;
    }

    private MQTTPayload generateCommandResponseData(MQTTPayload input, Group group, TaskType type, String[] tokens, LocalDateTime taskDateTime) {

        MQTTPayload outboundMessage = new MQTTPayload(input.getUid(), input.getGroupUid(),
                group.getGroupName(),
                "Grassroot", input.getTime(),null);

        if (TaskType.MEETING.equals(type)) {
            final String text = messageSourceAccessor.getMessage("gcm.xmpp.command.meeting", tokens);
            outboundMessage.setText(text);
            outboundMessage.setType(TaskType.MEETING.toString());

        } else if (TaskType.VOTE.equals(type)) {
            final String text = messageSourceAccessor.getMessage("gcm.xmpp.command.vote", tokens);
            outboundMessage.setText(text);
            outboundMessage.setType(TaskType.VOTE.toString());
        } else {
            final String text = messageSourceAccessor.getMessage("gcm.xmpp.command.todo", tokens);
            outboundMessage.setText(text);
            outboundMessage.setType(TaskType.TODO.toString());
        }
        outboundMessage.setTokens(Arrays.asList(tokens));

        if (taskDateTime != null) {

            ZonedDateTime zonedDateTime = taskDateTime.atZone(DateTimeUtil.getSAST());
            outboundMessage.setActionDateTime(Date.from(zonedDateTime.toInstant()));
        }

        return outboundMessage;
    }

    private MQTTPayload generateMessage(MQTTPayload input, Group group) {
        MQTTPayload data;
        final String msg = input.getText();
        final String[] tokens = MessageUtils.tokenize(msg);
        final TaskType cmdType = msg.contains("/meeting") ?
                TaskType.MEETING : msg.contains("/vote") ? TaskType.VOTE : TaskType.TODO;
        if (tokens.length < (TaskType.MEETING.equals(cmdType) ? 3 : 2)) {
            data = generateInvalidCommandResponseData(input, group);
        } else {
            try {
                final LocalDateTime parsedDateTime = learningService.parse(tokens[1]);
                if (DateTimeUtil.convertToSystemTime(parsedDateTime, DateTimeUtil.getSAST()).isBefore(Instant.now())) {
                    data = generateDateInPastData(input, group);
                } else {
                    tokens[1] = parsedDateTime.format(cmdMessageFormat);
                    data = generateCommandResponseData(input, group, cmdType, tokens, parsedDateTime);
                }
            } catch (SeloParseDateTimeFailure e) {
                data = generateInvalidCommandResponseData(input, group);
            }
        }

        return data;
    }




}
