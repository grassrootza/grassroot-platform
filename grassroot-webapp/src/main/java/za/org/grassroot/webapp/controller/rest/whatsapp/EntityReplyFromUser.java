package za.org.grassroot.webapp.controller.rest.whatsapp;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import za.org.grassroot.core.domain.geo.GeoLocation;

import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor @Getter @Setter @ToString
public class EntityReplyFromUser {

    private String userMessage;
    private String menuOptionPayload;
    private Map<String, String> auxProperties = new HashMap<>(); // for sending back things pertinent to domain, e.g., campaign prior msg UID
    private GeoLocation location;

}
