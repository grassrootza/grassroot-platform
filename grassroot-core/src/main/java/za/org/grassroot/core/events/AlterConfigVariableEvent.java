package za.org.grassroot.core.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AlterConfigVariableEvent extends ApplicationEvent {

    private String key;
    private boolean isCreationEvent;

    public AlterConfigVariableEvent(Object source, String key, boolean isCreationEvent) {
        super(source);
        this.isCreationEvent = isCreationEvent;
        this.key = key;
    }
}
