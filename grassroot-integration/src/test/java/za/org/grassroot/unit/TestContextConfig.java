package za.org.grassroot.unit;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.xmpp.config.XmppConnectionFactoryBean;
import org.springframework.messaging.MessageChannel;
import za.org.grassroot.core.repository.GcmRegistrationRepository;
import za.org.grassroot.core.repository.TodoRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.integration.GroupChatBroker;
import za.org.grassroot.integration.LearningService;
import za.org.grassroot.integration.MessageSendingService;
import za.org.grassroot.integration.NotificationService;
import za.org.grassroot.integration.mqtt.MqttSubscriptionService;
import za.org.grassroot.integration.messaging.SmsSendingService;
import za.org.grassroot.integration.GcmRegistrationBroker;

import static org.mockito.Mockito.mock;

/**
 * Created by paballo on 2016/04/12.
 * todo : switch these to inject mocks
 */
@Configuration
public class TestContextConfig {

    @Bean
    public FactoryBean<NotificationService> notificationService() {
        return new MockitoFactoryBean<>(NotificationService.class);
    }

    @Bean
    public FactoryBean<TodoRepository> logbookRepository() {
        return new MockitoFactoryBean<>(TodoRepository.class);
    }

    @Bean
    public FactoryBean<UserRepository> userRepository(){
        return new MockitoFactoryBean<>(UserRepository.class);
    }

    @Bean
    public FactoryBean<MessageChannel> messageChannel() {
        return new MockitoFactoryBean<>(MessageChannel.class);
    }

    @Bean
    public FactoryBean<MessageSendingService> messageSendingService() {
        return new MockitoFactoryBean<>(MessageSendingService.class);
    }

    @Bean
    public FactoryBean<SmsSendingService> smsSendingService() {
        return new MockitoFactoryBean<>(SmsSendingService.class);
    }

    @Bean
    public FactoryBean<GcmRegistrationRepository> gcmRegistrationRepository() {
        return new MockitoFactoryBean<>(GcmRegistrationRepository.class);
    }

    @Bean
    public FactoryBean<MqttSubscriptionService> mqttSubscriptionService() {
        return new MockitoFactoryBean<>(MqttSubscriptionService.class);
    }

   @Bean(name="mqttAdapter")
    public FactoryBean<MqttPahoMessageDrivenChannelAdapter> mqttAdapter(){
        return new MockitoFactoryBean<>(MqttPahoMessageDrivenChannelAdapter.class);
    }

    @Bean
    public FactoryBean<GcmRegistrationBroker> gcmService() {
        return new MockitoFactoryBean<>(GcmRegistrationBroker.class);
    }


    @Bean(name = "gcmConnection")
    public FactoryBean<XmppConnectionFactoryBean> gcmConnection() {
        return new MockitoFactoryBean<>(XmppConnectionFactoryBean.class);
    }

    @Bean()
    public FactoryBean<GroupChatBroker> messengerSettingsService() {
        return new MockitoFactoryBean<>(GroupChatBroker.class);
    }


    @Bean()
    public FactoryBean<LearningService> learningService(){
        return new MockitoFactoryBean<>(LearningService.class);
    }

    @Bean(name ="integrationMessageSourceAccessor")
    public FactoryBean<MessageSourceAccessor> messageSourceAccessor(){
        return new MockitoFactoryBean<>(MessageSourceAccessor.class);
    }

    @Bean(name ="integrationMessageSource")
    public FactoryBean<ResourceBundleMessageSource> messageSource(){
        return new MockitoFactoryBean<>(ResourceBundleMessageSource.class);
    }

    @Bean
    public static PropertyPlaceholderConfigurer propertyPlaceholderConfigurer() {
        PropertyPlaceholderConfigurer ppc = new PropertyPlaceholderConfigurer();
        ppc.setIgnoreResourceNotFound(true);
        return ppc;
    }

    class MockitoFactoryBean<T> implements FactoryBean<T> {
        private final Class<T> clazz;

        public MockitoFactoryBean(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public T getObject() throws Exception {
            return mock(clazz);
        }

        @Override
        public Class<T> getObjectType() {
            return clazz;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }
}
