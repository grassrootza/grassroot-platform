package za.org.grassroot.services.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter @AllArgsConstructor
public class PublicActivityLog {

    private PublicActivityType type;
    private String actorName;
    private long actionTimeMillis;

}
