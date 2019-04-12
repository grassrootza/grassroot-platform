package za.org.grassroot.core.events;

import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

@Getter @ToString
public class AlterConfigVariableEvent extends ApplicationEvent {

    private String key;
    private boolean isCreationEvent;

    public AlterConfigVariableEvent(Object source, String key, boolean isCreationEvent) {
        super(source);
        this.isCreationEvent = isCreationEvent;
        this.key = key;
    }
}
