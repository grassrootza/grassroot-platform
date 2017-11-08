package za.org.grassroot.webapp.model;

import lombok.Getter;
import za.org.grassroot.core.domain.UidIdentifiable;

@Getter
public class UidNameDTO {

    private final String uid;
    private final String name;

    public UidNameDTO(UidIdentifiable entity) {
        this.uid = entity.getUid();
        this.name = entity.getName();
    }

    public UidNameDTO(String uid, String name) {
        this.uid = uid;
        this.name = name;
    }

}
