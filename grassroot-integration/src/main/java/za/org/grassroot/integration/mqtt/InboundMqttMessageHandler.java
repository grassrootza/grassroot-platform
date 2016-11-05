package za.org.grassroot.integration.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.GroupChatService;
import za.org.grassroot.integration.LearningService;
import za.org.grassroot.integration.config.GrassrootIntegrationConfig;
import za.org.grassroot.integration.domain.MQTTPayload;
import za.org.grassroot.integration.exception.SeloParseDateTimeFailure;
import za.org.grassroot.integration.utils.MessageUtils;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;

/**
 * Created by paballo on 2016/11/04.
 */

@Configuration
@Import({MQTTConfig.class, GrassrootIntegrationConfig.class})
public class InboundMqttMessageHandler {


    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    @Qualifier("integrationMessageSourceAccessor")
    private MessageSourceAccessor messageSourceAccessor;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    GroupChatService groupChatService;

    @Autowired
    private MessageChannel mqttOutboundChannel;

    @Autowired
    private LearningService learningService;

    private static final DateTimeFormatter cmdMessageFormat = DateTimeFormatter.ofPattern("HH:mm, EEE d MMM");


    private static final Logger logger = LoggerFactory.getLogger(InboundMqttMessageHandler.class);

    @Bean
    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public MessageHandler mqttInboundMessageHandler() {
        return new MessageHandler() {
            @Override
            public void handleMessage(Message<?> message) throws MessagingException {
                try {
                    String topic = String.valueOf(message.getHeaders().get(MqttHeaders.TOPIC));
                    MQTTPayload payload = objectMapper.readValue(message.getPayload().toString(), MQTTPayload.class);
                    if(topic.equals("Grassroot")){
                        processIncomingChatMessage(payload);
                    }else{
                        //todo save message values to database and keep track of read status
                    }

                } catch (IOException e) {
                    logger.info("Error receiving message");
                }


            }
        };
    }


    //todo all the code below will be moved to groupchatmanager on cleanup
    private MQTTPayload generateInvalidCommandResponseData(MQTTPayload input, Group group) {

        String responseMessage = messageSourceAccessor.getMessage("gcm.xmpp.command.invalid");
        MQTTPayload outboundMessage = new MQTTPayload();
        outboundMessage.setUid(input.getUid());
        outboundMessage.setText(responseMessage);
        outboundMessage.setDisplayName("Grassroot");
        outboundMessage.setType("error");
        outboundMessage.setTime(input.getTime());
        outboundMessage.setGroupName(input.getGroupName());
        outboundMessage.setGroupUid(input.getGroupUid());

        return outboundMessage;
    }


    private MQTTPayload generateDateInPastData(MQTTPayload input, Group group) {

        String responseMessage = messageSourceAccessor.getMessage("gcm.xmpp.command.timepast");
        MQTTPayload outboundMessage = new MQTTPayload();
        outboundMessage.setUid(input.getUid());
        outboundMessage.setText(responseMessage);
        outboundMessage.setDisplayName("Grassroot");
        outboundMessage.setType("error");
        outboundMessage.setTime(input.getTime());
        outboundMessage.setGroupName(input.getGroupName());
        outboundMessage.setGroupUid(input.getGroupUid());
        return outboundMessage;
    }

    private MQTTPayload generateCommandResponseData(MQTTPayload input, Group group, TaskType type, String[] tokens, LocalDateTime taskDateTime) {

        MQTTPayload outboundMessage = new MQTTPayload();
        outboundMessage.setUid(input.getUid());
        outboundMessage.setDisplayName("Grassroot");
        outboundMessage.setType("error");
        outboundMessage.setTime(input.getTime());
        outboundMessage.setGroupName(group.getGroupName());
        outboundMessage.setGroupUid(input.getGroupUid());

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
            outboundMessage.setActionDateTime(new Date(taskDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        }

        return outboundMessage;
    }

    public MQTTPayload generateMessage(MQTTPayload input, Group group) {
        MQTTPayload data;
           final String msg = input.getText();
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

        return data;
    }

    public void processIncomingChatMessage(MQTTPayload incoming) {
        String phoneNumber = incoming.getPhoneNumber();
        String groupUid = incoming.getGroupUid();
        Group group = groupRepository.findOneByUid(groupUid);
        MQTTPayload messageData = generateMessage(incoming, group);
        ObjectMapper objectMapper = new ObjectMapper();
        String message;
        try {
            message = objectMapper.writeValueAsString(messageData);
            mqttOutboundChannel.send(MessageBuilder.withPayload(message).
                    setHeader(MqttHeaders.TOPIC, incoming.getPhoneNumber()).build());
        } catch (JsonProcessingException e) {
            logger.debug("Error sending message over mqtt, got error message ={}", e.getMessage());
        }
       ;


    }
}
