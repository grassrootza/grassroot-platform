package za.org.grassroot.integration;

import org.jivesoftware.smack.packet.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupChatSettings;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupChatSettingsRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.integration.domain.AndroidClickActionType;
import za.org.grassroot.integration.domain.IncomingChatMessage;
import za.org.grassroot.integration.exception.GroupChatSettingNotFoundException;
import za.org.grassroot.integration.exception.SeloParseDateTimeFailure;
import za.org.grassroot.integration.utils.MessageUtils;
import za.org.grassroot.integration.xmpp.GcmXmppMessageCodec;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Created by paballo on 2016/09/08.
 */
@Service
public class GroupChatManager implements GroupChatService {

    private static final Logger logger = LoggerFactory.getLogger(GroupChatManager.class);

    @Value("${gcm.topics.path}")
    private String TOPICS;

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
    @Qualifier("integrationMessageSourceAccessor")
    MessageSourceAccessor messageSourceAccessor;

    @Override
    @Transactional
    public void createUserGroupMessagingSetting(String userUid, String groupUid, boolean active, boolean canSend, boolean canReceive) {
        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user =  userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = new GroupChatSettings(user,group,active,true,true,true);
        groupChatSettingsRepository.save(groupChatSettings);
    }

    @Override
    public void processAndRouteIncomingChatMessage(IncomingChatMessage incoming) {
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

    public org.springframework.messaging.Message<Message> generateCannotSendMessage(IncomingChatMessage input, Group group){
        Map<String, Object> data = MessageUtils.generateUserMutedResponseData(messageSourceAccessor, input, group);
        return GcmXmppMessageCodec.encode(input.getFrom(), String.valueOf(data.get("messageId")),
                null, null, null, AndroidClickActionType.CHAT_MESSAGE.name(), data);
    }

    public org.springframework.messaging.Message<Message> generateMessage(User user, IncomingChatMessage input, Group group) {
        org.springframework.messaging.Message<Message> gcmMessage;
        Map<String, Object> data;
        if (!MessageUtils.isCommand((input))) {
            String topic = TOPICS.concat(group.getUid());
            data = MessageUtils.generateChatMessageData(input, user, group);
            gcmMessage = GcmXmppMessageCodec.encode(topic, String.valueOf(data.get("messageId")),
                    null, null, null, AndroidClickActionType.CHAT_MESSAGE.name(), data);
        } else {
            String[] tokens = MessageUtils.tokenize(String.valueOf(input.getData().get("message")));
            if (tokens.length < 2) {
                data = MessageUtils.generateInvalidCommandResponseData(messageSourceAccessor,input, group);
            } else {
                try {
                    tokens[1] = learningService.parse(tokens[1]).toString();
                    data = MessageUtils.generateCommandResponseData(messageSourceAccessor,input, group, tokens);
                } catch (SeloParseDateTimeFailure e) {
                    data = MessageUtils.generateInvalidCommandResponseData(messageSourceAccessor,input, group);
                }
            }
            gcmMessage = GcmXmppMessageCodec.encode(input.getFrom(), String.valueOf(data.get("messageId")),
                    null, null, null, AndroidClickActionType.CHAT_MESSAGE.name(), data);
        }

        return gcmMessage;
    }


    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "groupChatSettings",key = "userUid + '_'+ groupUid")
    public GroupChatSettings load(String userUid, String groupUid) throws GroupChatSettingNotFoundException {
        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user =  userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = groupChatSettingsRepository.findByUserAndGroup(user, group);

        if(groupChatSettings == null){
            throw  new GroupChatSettingNotFoundException("Group chat setting not found found for user with uid " + userUid);
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

        GroupChatSettings groupChatSettings = groupChatSettingsRepository.findByUserAndGroup(user,group);
        if(null== groupChatSettings){
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

        GroupChatSettings groupChatSettings = groupChatSettingsRepository.findByUserAndGroup(user,group);
        if(null== groupChatSettings){
            throw new GroupChatSettingNotFoundException("Message settings not found for user with uid " + userUid);
        }
        groupChatSettings.setActive(active);
        groupChatSettings.setUserInitiated(userInitiated);
        groupChatSettings.setCanSend(active);
        if(userInitiated){
            groupChatSettings.setCanReceive(active);
        }
        groupChatSettingsRepository.save(groupChatSettings);

    }

    @Override
    @Transactional(readOnly = true)
    public boolean messengerSettingExist(String userUid, String groupUid){

        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = groupChatSettingsRepository.findByUserAndGroup(user,group);
        return (groupChatSettings != null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupChatSettings> loadUsersToBeUnmuted(){
        return  groupChatSettingsRepository.findByActiveAndUserInitiatedAndReactivationTimeBefore(false,false, Instant.now());

    }

    @Override
    public List<String> usersMutedInGroup(String groupUid) {
        Objects.nonNull(groupUid);
        Group group = groupRepository.findOneByUid(groupUid);
        List<GroupChatSettings> groupChatSettingses =  groupChatSettingsRepository.findByGroupAndActiveAndCanSend(group,true,false);
        List<String> mutedUsersUids = new ArrayList<>();
        for(GroupChatSettings groupChatSettings: groupChatSettingses){
            User user = groupChatSettings.getUser();
            mutedUsersUids.add(user.getUsername());
        }
        return mutedUsersUids;
    }


}
