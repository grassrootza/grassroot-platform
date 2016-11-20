package za.org.grassroot.integration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * Created by paballo on 2016/04/01.
 */
@Configuration
@ComponentScan
@IntegrationComponentScan
@EnableIntegration
@PropertySource(value = "file:${user.home}/grassroot/grassroot-integration.properties", ignoreResourceNotFound = true) // else testing in CI fails
public class InfrastructureConfiguration {

    @Bean(name = PollerMetadata.DEFAULT_POLLER)
    public PollerMetadata defaultPoller() {
        PollerMetadata pollerMetadata = new PollerMetadata();
        pollerMetadata.setTrigger(new PeriodicTrigger(50));
        return pollerMetadata;
    }

    @Bean
    public MessageChannel gcmOutboundChannel(){
        return new DirectChannel();
    }

    @Bean
    public MessageChannel gcmXmppOutboundChannel(){
        return new DirectChannel();
    }

    @Bean
    public MessageChannel requestChannel(){return new QueueChannel();}

    @Bean
    public MessageChannel gcmInboundChannel(){return new DirectChannel();}

    @Bean
    public MessageChannel smsOutboundChannel(){return new DirectChannel();}

    @Bean
    public MessageChannel systemMessageChannel(){return new DirectChannel();}

    @Bean
    public MessageChannel emailOutboundChannel() { return new DirectChannel(); }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }



}
