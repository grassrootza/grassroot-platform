package za.org.grassroot.core.events;

import org.springframework.context.ApplicationEvent;

public class UpdateConfigVariableEvent extends ApplicationEvent {

    private String key;

    public UpdateConfigVariableEvent(Object source,String key){
        super(source);
        this.key = key;
    }

    public String getKey(){
        return this.key;
    }
}
