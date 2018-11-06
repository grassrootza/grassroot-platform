package za.org.grassroot.core.events;

import org.springframework.context.ApplicationEvent;

public class RemoveConfigVariableEvent extends ApplicationEvent {

    private String message;

    public RemoveConfigVariableEvent(Object src, String message){
        super(src);
        this.message = message;
    }

    public String getMessage() {
        return this.message;
    }
}
