package za.org.grassroot.services.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
public class PublicActivityLog {

    private PublicActivityType type;
    private String actorName;
    private long actionTimeMillis;

    public PublicActivityLog(PublicActivityType type, String actorName, long actionTimeMillis) {
        this.type = type;
//        this.actorName = actorName;
        this.actorName = "Someone"; // pseudonym (may make public in future for some types)
        this.actionTimeMillis = actionTimeMillis;
    }
}
