package za.org.grassroot.integration.mqtt;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageHandler;
import za.org.grassroot.integration.GroupChatService;
import za.org.grassroot.integration.config.GrassrootIntegrationConfig;
import za.org.grassroot.integration.domain.MQTTPayload;

import java.io.IOException;

/**
 * Created by paballo on 2016/11/04.
 */

@Configuration
@Import({MQTTConfig.class, GrassrootIntegrationConfig.class})
public class InboundMqttMessageHandler {

    private ObjectMapper payloadMapper;
    private GroupChatService groupChatService;

    @Autowired
    public InboundMqttMessageHandler(GroupChatService groupChatService, ObjectMapper payloadMapper) {
        this.groupChatService = groupChatService;
        this.payloadMapper = payloadMapper;
    }

    private static final Logger logger = LoggerFactory.getLogger(InboundMqttMessageHandler.class);

    @Bean
    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public MessageHandler mqttInboundMessageHandler() {
        return message -> {
            try {
                logger.info("incoming payload " + message.getPayload().toString());
                MQTTPayload payload = payloadMapper.readValue(message.getPayload().toString(), MQTTPayload.class);
                String topic = String.valueOf(message.getHeaders().get(MqttHeaders.TOPIC));
                if (topic.equals("Grassroot")) {
                    groupChatService.processCommandMessage(payload);
                } else {
                    groupChatService.createGroupChatMessageStats(payload);
                }
            } catch (IOException e) {
                logger.info("Error receiving message over mqtt");
                e.printStackTrace();
            }

        };
    }

}
