package za.org.grassroot.core.events;

import org.springframework.context.ApplicationEvent;

public class ConfigVariableEvent extends ApplicationEvent {

    private String key;
    private boolean create;

    public ConfigVariableEvent(Object source,String key,boolean create) {
        super(source);
        this.create = create;
        this.key = key;
    }

    public boolean getCreate() {
        return this.create;
    }

    public String getKey() {
        return this.key;
    }
}
