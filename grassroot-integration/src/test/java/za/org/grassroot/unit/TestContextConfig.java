package za.org.grassroot.unit;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.xmpp.config.XmppConnectionFactoryBean;
import za.org.grassroot.core.repository.GcmRegistrationRepository;
import za.org.grassroot.core.repository.TodoRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.integration.services.GcmService;
import za.org.grassroot.integration.services.MessengerSettingsService;
import za.org.grassroot.integration.services.NotificationService;
import za.org.grassroot.integration.services.SmsSendingService;

import static org.mockito.Mockito.mock;

/**
 * Created by paballo on 2016/04/12.
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
    public FactoryBean<SmsSendingService> smsSendingService() {
        return new MockitoFactoryBean<>(SmsSendingService.class);
    }

    @Bean
    public FactoryBean<GcmRegistrationRepository> gcmRegistrationRepository() {
        return new MockitoFactoryBean<>(GcmRegistrationRepository.class);
    }

    @Bean
    public FactoryBean<GcmService> gcmService() {
        return new MockitoFactoryBean<>(GcmService.class);
    }

    @Bean
    public FactoryBean<MessengerSettingsService> messengerSettingsService() {
        return new MockitoFactoryBean<>(MessengerSettingsService.class);
    }



    @Bean(name = "gcmConnection")
    public FactoryBean<XmppConnectionFactoryBean> gcmConnection() {
        return new MockitoFactoryBean<>(XmppConnectionFactoryBean.class);
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
