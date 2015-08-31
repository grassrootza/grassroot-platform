package za.org.grassroot.core.event;

import org.springframework.context.ApplicationEvent;
import za.org.grassroot.core.domain.Event;

/**
 * Created by aakilomar on 8/30/15.
 */
public class EventChangeEvent extends ApplicationEvent {

    private String type;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public EventChangeEvent(Event event) {
        super(event);
    }

    public EventChangeEvent(Event event, String type) {
        super(event);
        this.type = type;
    }

    @Override
    public String toString() {
        return "ChangeEvent{" +
                "type='" + type + '\'' +
//                ",event='" + getSource().toString() + '\'' +
                '}';
    }
}
