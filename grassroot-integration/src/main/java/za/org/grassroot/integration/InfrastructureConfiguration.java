package za.org.grassroot.integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.test.context.BootstrapWith;

/**
 * Created by paballo on 2016/04/01.
 */
@Configuration
@ComponentScan
@IntegrationComponentScan
@EnableIntegration
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





}
