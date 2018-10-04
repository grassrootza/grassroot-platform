package za.org.grassroot.core.events;

import org.springframework.context.ApplicationEvent;

public class CreateConfigVariableEvent extends ApplicationEvent {

    private String key;

    public CreateConfigVariableEvent(Object source,String key) {
        super(source);
        this.key = key;
    }

    public String getKey(){
        return this.key;
    }
}
