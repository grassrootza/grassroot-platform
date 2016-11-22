package za.org.grassroot.integration.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;

import java.text.SimpleDateFormat;

/**
 * Created by paballo on 2016/10/28.
 */

@Configuration
@ComponentScan
@IntegrationComponentScan
@EnableIntegration
@ConditionalOnProperty(name = "mqtt.connection.enabled", havingValue = "true")
public class MQTTConfig {

    @Value("${mqtt.connection.url}")
    private String host;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setServerURIs(host);
        factory.setCleanSession(false);
        return factory;
    }

    @Bean
    public MessageProducerSupport messageProducerSupport(){
        return mqttAdapter();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttAdapter(){
        MqttPahoMessageDrivenChannelAdapter adapter
                = new MqttPahoMessageDrivenChannelAdapter("Grassroot",
                mqttClientFactory());
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setOutputChannel(mqttInboundChannel());
        adapter.setQos(1);
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MqttPahoMessageHandler mqttOutbound() {
        MqttPahoMessageHandler messageHandler =
                new MqttPahoMessageHandler("Grassroot Server", mqttClientFactory());
        messageHandler.setAsync(true);
        messageHandler.setConverter(new DefaultPahoMessageConverter());
        return messageHandler;
    }

    @Bean
    public MessageChannel mqttInboundChannel(){
        return  new DirectChannel();
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @Primary
    public ObjectMapper payloadMapper() {
        ObjectMapper payloadMapper = new ObjectMapper();
        payloadMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"));
        /*SimpleModule ldtModule = new SimpleModule();
        ldtModule.addSerializer(LocalDateTime.class, new LDTSerializer());
        ldtModule.addDeserializer(LocalDateTime.class, new LDTDeserializer());
        payloadMapper.registerModule(ldtModule);*/
        return payloadMapper;
    }

}




