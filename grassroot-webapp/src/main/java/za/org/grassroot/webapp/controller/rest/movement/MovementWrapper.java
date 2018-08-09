package za.org.grassroot.webapp.controller.rest.movement;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import za.org.grassroot.core.domain.movement.Movement;

@Getter @Slf4j @ToString
public class MovementWrapper {

    private final String uid;
    private final String name;

    public MovementWrapper(Movement movement) {
        this.uid = movement.getUid();
        this.name = movement.getName();
    }

}
