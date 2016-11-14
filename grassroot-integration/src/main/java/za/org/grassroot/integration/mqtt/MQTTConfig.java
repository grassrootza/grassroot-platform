package za.org.grassroot.integration.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Set;

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

  /*  @Value("$mqtt.connection.username")
    private String userName;

    @Value("$mqtt.conection.password")
    private String password;
*/

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setServerURIs(host);
        factory.setCleanSession(false);
      //  factory.setUserName();
      //  factory.setPassword();
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

    @Bean("payloadMapper")
    public ObjectMapper payloadMapper(){
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDateFormat(new SimpleDateFormat("MMM dd, yyyy hh:mm:ss a", Locale.UK));
        return mapper;
    }




}




