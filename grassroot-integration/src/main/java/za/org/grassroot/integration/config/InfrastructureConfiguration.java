package za.org.grassroot.integration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.messaging.MessageChannel;

/**
 * Created by paballo on 2016/04/01.
 */
@Configuration
@ComponentScan
@IntegrationComponentScan
@EnableIntegration
public class InfrastructureConfiguration {


    @Bean
    public MessageChannel gcmOutboundChannel(){
        return new DirectChannel();
    }

    @Bean
    public MessageChannel gcmXmppOutboundChannel(){
        return new DirectChannel();
    }

    @Bean
    public MessageChannel requestChannel(){return new DirectChannel();}

    @Bean
    public MessageChannel gcmInboundChannel(){return new DirectChannel();}

    @Bean
    public MessageChannel smsOutboundChannel(){return new DirectChannel();}

    @Bean
    public MessageChannel systemMessageChannel(){return new DirectChannel();}




}
