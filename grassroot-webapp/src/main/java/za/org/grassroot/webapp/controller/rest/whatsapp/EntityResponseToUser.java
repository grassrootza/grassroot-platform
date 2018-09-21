package za.org.grassroot.webapp.controller.rest.whatsapp;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import za.org.grassroot.core.domain.JpaEntityType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter @Setter @Builder @ToString
public class EntityResponseToUser {

    private JpaEntityType entityType;
    private String entityUid;
    private List<String> messages; // in future can include media links etc
    private LinkedHashMap<String, String> menu; // payload then message - ordering is important, hence enforcing LinkedHashMap
    private RequestDataType requestDataType;
    private Map<String, String> auxProperties; // for storing things like message IDs etc. Key = message, key = payload, to enforce principle of not giving user redundant options

    public static EntityResponseToUser cannotRespond(JpaEntityType entityType, String entityUid) {
        return EntityResponseToUser.builder().entityType(entityType).entityUid(entityUid).build();
    }

}
