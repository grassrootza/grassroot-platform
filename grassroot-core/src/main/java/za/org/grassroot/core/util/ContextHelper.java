package za.org.grassroot.core.util;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;

@Component
public class ContextHelper implements ApplicationEventPublisherAware {

    private static ApplicationEventPublisher publisher;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        ContextHelper.publisher = applicationEventPublisher;
    }

    public static ApplicationEventPublisher getPublisher() {
        return publisher;
    }

}
