package za.org.grassroot.integration.graph;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Task;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.enums.ActorType;
import za.org.grassroot.graph.domain.enums.EventType;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.enums.GrassrootRelationship;
import za.org.grassroot.graph.dto.ActionType;
import za.org.grassroot.graph.dto.IncomingDataObject;
import za.org.grassroot.graph.dto.IncomingGraphAction;
import za.org.grassroot.graph.dto.IncomingRelationship;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service @Slf4j
@ConditionalOnProperty("grassroot.graph.enabled")
public class GraphBrokerImpl implements GraphBroker {

    private AmazonSQS sqs;
    private ObjectMapper objectMapper;

    @Value("${grassroot.graph.sqs.queue:grassroot-graph-test}")
    private String grassrootQueue;
    private String sqsQueueUrl;

    public GraphBrokerImpl() {
        log.info("Graph broker enabled, constructing object mapper ...");
        this.objectMapper = new ObjectMapper().findAndRegisterModules()
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
    }

    @PostConstruct
    public void init() {
        this.sqs = AmazonSQSClientBuilder.defaultClient();
        this.sqsQueueUrl = sqs.getQueueUrl(grassrootQueue).getQueueUrl();
    }

    @Override
    public void addUserToGraph(String userUid) {
        log.info("adding user to Grassroot graph ... {}", userUid);
        Actor actor = new Actor(ActorType.INDIVIDUAL, userUid);
        IncomingGraphAction action = wrapActorCreation(actor);
        dispatchAction(action, "user");
    }

    @Override
    public void addGroupToGraph(String groupUid, String creatingUserUid) {
        log.info("adding group to Grassroot graph ...");
        Actor group = new Actor(ActorType.GROUP, groupUid);
        IncomingGraphAction action = wrapActorCreation(group);
        IncomingRelationship genRel = generatorRelationship(creatingUserUid, groupUid);
        action.addRelationship(genRel);
        dispatchAction(action, "group");
    }

    @Override
    public void addMovementToGraph(String movementUid, String creatingUserUid) {
        log.info("adding movement to Grassroot graph ...");
        Actor movement = new Actor(ActorType.MOVEMENT, movementUid);
        IncomingGraphAction action = wrapActorCreation(movement);
        IncomingRelationship genRel = generatorRelationship(creatingUserUid, movementUid);
        action.addRelationship(genRel);
        dispatchAction(action, "movement");
    }

    @Override
    public void addAccountToGraph(String accountUid, List<String> adminUids) {
        log.info("adding an extra account to Grassroot graph ...");
        Actor account = new Actor(ActorType.ACCOUNT, accountUid);
        IncomingGraphAction action = wrapActorCreation(account);
        List<IncomingRelationship> relationships = adminUids.stream()
                .map(adminUid -> generatorRelationship(adminUid, accountUid)).collect(Collectors.toList());
        action.setRelationships(relationships);
        dispatchAction(action, "account");
    }

    @Override
    public void addMembershipToGraph(String userUid, String groupUid) {
        IncomingRelationship relationship = new IncomingRelationship(userUid, GraphEntityType.ACTOR,
                groupUid, GraphEntityType.ACTOR, GrassrootRelationship.Type.PARTICIPATES);
        IncomingGraphAction graphAction = new IncomingGraphAction(userUid, ActionType.CREATE_RELATIONSHIP,
                null, Collections.singletonList(relationship));

        log.info("about to dispatch adding membership: userId = {}, graphId = {}", userUid, groupUid);
        dispatchAction(graphAction, "membership");
    }

    @Override
    public void removeMembershipFromGraph(String userUid, String groupUid) {
        IncomingRelationship relationship = new IncomingRelationship(userUid, GraphEntityType.ACTOR,
                groupUid, GraphEntityType.ACTOR, GrassrootRelationship.Type.PARTICIPATES);
        IncomingGraphAction graphAction = new IncomingGraphAction(userUid, ActionType.REMOVE_RELATIONSHIP,
                null, Collections.singletonList(relationship));

        log.info("about to dispatch removing membership: userId= {}, graphId = {}", userUid, groupUid);
        dispatchAction(graphAction, "remove membership");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addTaskToGraph(Task task) {
        log.info("adding a task to Grassroot Graph ... ");
        List<IncomingDataObject> graphDataObjects = new ArrayList<>();

        final TaskType taskType = task.getTaskType();
        EventType graphEventType = TaskType.TODO.equals(taskType) ? EventType.TODO :
                TaskType.VOTE.equals(taskType) ? EventType.VOTE : EventType.MEETING;
        Event graphEvent = new Event(graphEventType, task.getUid(), task.getDeadlineTime().toEpochMilli());

        Actor creatingUser = new Actor(ActorType.INDIVIDUAL, task.getCreatedByUser().getUid());
        graphEvent.setCreator(creatingUser);

        final Set<User> assignedMembers = (Set<User>) task.getMembers();
        List<Actor> participatingActors = assignedMembers.stream()
                .map(u -> new Actor(ActorType.INDIVIDUAL, u.getUid())).collect(Collectors.toList());
        graphEvent.setParticipants(participatingActors);
        log.info("processed {} assigned members, participants: {}", assignedMembers.size(), graphEvent.getParticipants());

        final Group parentGroup = task.getAncestorGroup();
        Actor graphParent = new Actor(ActorType.GROUP, parentGroup.getUid());
        graphEvent.setParticipatesIn(Collections.singletonList(graphParent));

        // note: neo4j on other end is supposed to take care of these relationships
        graphDataObjects.add(new IncomingDataObject(GraphEntityType.ACTOR, creatingUser));
        graphDataObjects.addAll(participatingActors.stream().map(a -> new IncomingDataObject(GraphEntityType.ACTOR, a)).collect(Collectors.toList()));
        graphDataObjects.add(new IncomingDataObject(GraphEntityType.ACTOR, graphParent));
        graphDataObjects.add(new IncomingDataObject(GraphEntityType.EVENT, graphEvent));

        IncomingGraphAction graphAction = new IncomingGraphAction(task.getUid(), ActionType.CREATE_ENTITY,
                graphDataObjects, null);



        dispatchAction(graphAction, "task");
    }

    private IncomingGraphAction wrapActorCreation(Actor actor) {
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.ACTOR, actor);
        return new IncomingGraphAction(actor.getPlatformUid(),
                ActionType.CREATE_ENTITY,
                new ArrayList<>(Collections.singletonList(dataObject)), // to avoid nulls on later adds
                new ArrayList<>());
    }

    private IncomingRelationship generatorRelationship(String generatorUid, String generatedUid) {
        return new IncomingRelationship(generatorUid, GraphEntityType.ACTOR,
                generatedUid, GraphEntityType.ACTOR, GrassrootRelationship.Type.GENERATOR);
    }

    private void dispatchAction(IncomingGraphAction action, String actionDescription) {
        try {
            sqs.sendMessage(new SendMessageRequest(sqsQueueUrl, objectMapper.writeValueAsString(action)));
            log.info("successfully dispatched {} to graph entity queue ...", actionDescription);
        } catch (JsonProcessingException e) {
            log.error("error adding graph action to queue ... ", e);
        }
    }

}
