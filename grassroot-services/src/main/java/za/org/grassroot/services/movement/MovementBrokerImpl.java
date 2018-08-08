package za.org.grassroot.services.movement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.movement.Movement;
import za.org.grassroot.core.domain.movement.MovementPermissionType;
import za.org.grassroot.core.repository.MovementRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.integration.graph.GraphBroker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service @Slf4j
@ConditionalOnProperty("grassroot.movements.enabled")
public class MovementBrokerImpl implements MovementBroker {

    private final UserRepository userRepository;
    private final MovementRepository movementRepository;
    private GraphBroker graphBroker;

    @Autowired
    public MovementBrokerImpl(UserRepository userRepository, MovementRepository movementRepository) {
        this.userRepository = userRepository;
        this.movementRepository = movementRepository;
    }

    @Autowired(required = false)
    public void setGraphBroker(GraphBroker graphBroker) {
        this.graphBroker = graphBroker;
    }

    @Override
    @Transactional(readOnly = true)
    public Movement load(String movementUid) {
        return movementRepository.findOneByUid(movementUid);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Movement> loadUserMovements(String userUid) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        return movementRepository.findByOrganizers(user);
    }

    @Override
    public String createMovement(String userUid, String name, MovementPermissionType permissionType) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        if (user.getPrimaryAccount() == null || !user.getPrimaryAccount().isEnabled()) {
            throw new IllegalArgumentException("Error! ");
        }
        Movement movement = movementRepository.save(new Movement(name, user));
        if (graphBroker != null) {
            graphBroker.addMovementToGraph(movement.getUid(), user.getUid());
        }
        return movement.getUid();
    }

    @Override
    public void alterPermissionType(String userUid, String movementUid, MovementPermissionType permissionType) {
        log.info("Set some permissions");
    }

    @Override
    public void addOrganizer(String userUid, String movementUid, String organizerUid) {
        log.info("Add an organizer");
    }

    @Override
    public void addMembers(String userUid, String movementUid, Collection<String> membersToAddUid) {
        log.info("Add a member");
    }

    @Override
    public void addGroup(String userUid, String movementUid, String groupUid) {
        log.info("Add a group");
    }

    @Override
    public void requestToJoin(String userUid, String movementUid) {
        log.info("Ask to join movement");
    }

    @Override
    public void requestToAddGroup(String userUid, String movementUid) {
        log.info("Ask to add group to movement");
    }

    @Override
    public void approveRequestToAddGroup(String userUid, String movementUid, String groupUid) {
        log.info("Approve request to add group to movement");
    }
}
