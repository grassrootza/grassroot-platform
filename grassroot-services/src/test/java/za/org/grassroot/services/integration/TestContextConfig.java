package za.org.grassroot.services.integration;

import org.jivesoftware.smack.XMPPConnection;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import static org.mockito.Mockito.mock;

/**
 * Created by paballo on 2016/04/12.
 */
@Component
public class TestContextConfig {

    @Bean
    public FactoryBean<XMPPConnection> gcmConnection() {
        return new MockitoFactoryBean<>(XMPPConnection.class);
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