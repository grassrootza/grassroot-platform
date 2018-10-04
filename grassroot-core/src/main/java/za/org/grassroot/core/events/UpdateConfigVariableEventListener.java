package za.org.grassroot.core.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component @Slf4j
public class UpdateConfigVariableEventListener implements ApplicationListener<UpdateConfigVariableEvent> {

    @Override
    public void onApplicationEvent(UpdateConfigVariableEvent event) {
        log.info("Updated config variable with key = {}",event.getKey());
    }
}
