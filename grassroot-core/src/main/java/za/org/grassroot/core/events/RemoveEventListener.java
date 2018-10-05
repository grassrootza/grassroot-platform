package za.org.grassroot.core.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component @Slf4j
public class RemoveEventListener implements ApplicationListener<RemoveConfigVariableEvent> {
    @Override
    public void onApplicationEvent(RemoveConfigVariableEvent event) {
        log.info("Trying to delete CV {}",event.getMessage());
    }
}
