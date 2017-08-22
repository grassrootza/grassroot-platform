package za.org.grassroot.webapp.model;

import za.org.grassroot.core.domain.UidIdentifiable;

public class UidNameDTO {

    private final String uid;
    private final String name;

    public UidNameDTO(UidIdentifiable entity) {
        this.uid = entity.getUid();
        this.name = entity.getName();
    }

    public String getUid() {
        return uid;
    }

    public String getName() {
        return name;
    }
}
