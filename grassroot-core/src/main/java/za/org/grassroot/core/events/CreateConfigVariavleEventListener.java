package za.org.grassroot.core.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component @Slf4j
public class CreateConfigVariavleEventListener implements ApplicationListener<CreateConfigVariableEvent> {
    @Override
    public void onApplicationEvent(CreateConfigVariableEvent event) {
       log.info("Created config variable with key = {}", event.getKey());
    }
}
