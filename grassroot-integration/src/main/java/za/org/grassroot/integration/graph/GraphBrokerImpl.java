package za.org.grassroot.integration.graph;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.LazyInitializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.task.Task;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.enums.ActorType;
import za.org.grassroot.graph.domain.enums.EventType;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.enums.GrassrootRelationship;
import za.org.grassroot.graph.dto.ActionType;
import za.org.grassroot.graph.dto.IncomingDataObject;
import za.org.grassroot.graph.dto.IncomingGraphAction;
import za.org.grassroot.graph.dto.IncomingRelationship;
import za.org.grassroot.graph.dto.IncomingAnnotation;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service @Slf4j
@ConditionalOnProperty("grassroot.graph.enabled")
public class GraphBrokerImpl implements GraphBroker {

    private AmazonSQS sqs;
    private ObjectMapper objectMapper;

    @Value("${grassroot.graph.sqs.queue:grassroot-graph-test}")
    private String grassrootQueue;
    private String sqsQueueUrl;
    private boolean isQueueFifo;

    public GraphBrokerImpl() {
        log.info("Graph broker enabled, constructing object mapper ...");
        this.objectMapper = new ObjectMapper().findAndRegisterModules()
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
    }

    @PostConstruct
    public void init() {
        this.sqs = AmazonSQSClientBuilder.defaultClient();
        this.sqsQueueUrl = sqs.getQueueUrl(grassrootQueue).getQueueUrl();
        this.isQueueFifo = grassrootQueue.endsWith(".fifo");
    }

    @Override
    public void addUserToGraph(String userUid) {
        log.info("adding user to Grassroot graph ... {}", userUid);

        IncomingGraphAction action = wrapEntityCreation(new Actor(ActorType.INDIVIDUAL, userUid));

        dispatchAction(action, "user");
    }

    @Override
    public void addUserAnnotation(User user) {
        log.info("adding user annotation to graph ... {}", user.getUid());

        Map<String, String> properties = new HashMap<>();
        properties.put(IncomingAnnotation.language, user.getLanguageCode());
        String province = Province.CANONICAL_NAMES_ZA.get(user.getProvince());
        properties.put(IncomingAnnotation.province, province);

        addEntityAnnotation(new Actor(ActorType.INDIVIDUAL, user.getUid()), properties, null);
    }

    @Override
    public void removeUserFromGraph(String userUid) {
        log.info("removing user from Grassroot graph ... {}", userUid);
        removeEntity(new Actor(ActorType.INDIVIDUAL, userUid));
    }

    @Override
    public void removeUserAnnotation(String userUid, Set<String> keysToRemove) {
        log.info("removing user annotation from graph ... {}", user.getUid());
        removeEntityAnnotation(new Actor(ActorType.INDIVIDUAL, userUid), keysToRemove, null);
    }

    @Override
    public void addGroupToGraph(String groupUid, String creatingUserUid, Set<String> memberUids) {
        log.info("adding group to Grassroot graph ... {}", groupUid);

        IncomingGraphAction action = wrapEntityCreation(new Actor(ActorType.GROUP, groupUid));
        addParticipants(action, memberUids, GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(),
                groupUid, GraphEntityType.ACTOR, ActorType.GROUP.name());
        action.addRelationship(generatorRelationship(creatingUserUid, GraphEntityType.ACTOR,
                ActorType.INDIVIDUAL.name(), groupUid, GraphEntityType.ACTOR, ActorType.GROUP.name()));

        dispatchAction(action, "group");
    }

    // Groups don't store single latitude/longitude, but center location is calculated for each group
    // nightly, so parameters for this location are set by calls from geo-location broker each night.
    @Override
    public void addGroupAnnotation(Group group, double latitude, double longitude) {
        log.info("adding group annotation to graph ... {}", group.getUid());

        Map<String, String> properties = new HashMap<>();
        properties.put(IncomingAnnotation.language, group.getDefaultLanguage());
        properties.put(IncomingAnnotation.latitude, latitude);
        properties.put(IncomingAnnotation.longitude, longitude);
        List<String> tags = (group.getTags() == null || group.getTags().length == 0) ?
                new ArrayList<>() : new ArrayList<>(Arrays.asList(group.getTags()));
        if (group.getDescription() != null) tags.add(group.getDescription()); // description to be processed

        addEntityAnnotation(new Actor(ActorType.GROUP, group.getUid()), properties, tags);
    }

    @Override
    public void removeGroupFromGraph(String groupUid) {
        log.info("removing group from Grassroot graph ... {}", groupUid);
        removeEntity(new Actor(ActorType.GROUP, groupUid));
    }

    @Override
    public void removeGroupAnnotation(String groupUid, Set<String> keysToRemove, List<String> tagsToRemove) {
        log.info("removing group annotation from graph ... {}", Uid);
        removeEntityAnnotation(new Actor(ActorType.GROUP, groupUid), keysToRemove, tagsToRemove);
    }

    @Override
    public void addAccountToGraph(String accountUid, List<String> adminUids) {
        log.info("adding account to Grassroot graph ... {}", accountUid);

        IncomingGraphAction action = wrapEntityCreation(new Actor(ActorType.ACCOUNT, accountUid));
        addGenerators(action, new HashSet<String>(adminUids), GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(),
                accountUid, GraphEntityType.ACTOR, ActorType.ACCOUNT.name());

        dispatchAction(action, "account");
    }

    @Override
    public void removeAccountFromGraph(String accountUid) {
        log.info("removing account from Grassroot graph ... {}", accountUid);
        removeEntity(new Actor(ActorType.ACCOUNT, accountUid));
    }

    @Override
    public void addMembershipToGraph(Set<String> memberUids, String groupUid) {
        log.info("adding membership to Grassroot graph ...");

        IncomingGraphAction action = new IncomingGraphAction(groupUid, ActionType.CREATE_RELATIONSHIP,
                null, new ArrayList<>(), null);
        addParticipants(action, memberUids, GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(),
                groupUid, GraphEntityType.ACTOR, ActorType.GROUP.name());

        dispatchAction(action, "membership");
    }

    @Override
    public void addMembershipAnnotation(Membership membership) {
        log.info("adding membership annotation to graph ...");

        IncomingRelationship relationship = participatingRelationship(membership.getUser().getUid(), GraphEntityType.ACTOR,
                ActorType.INDIVIDUAL, membership.getGroup().getUid(), GraphEntityType.ACTOR, ActorType.GROUP);
        List<String> tags = (membership.getTags() == null || membership.getTags().length == 0) ?
                new ArrayList<>() : new ArrayList<>(Arrays.asList(membership.getTags()));

        addRelationshipAnnotation(relationship, tags);
    }

    @Override
    public void removeMembershipFromGraph(String userUid, String groupUid) {
        log.info("removing membership from Grassroot graph ...");
        removeParticipationRelationship(userUid, GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(),
                groupUid, GraphEntityType.ACTOR, ActorType.GROUP.name());
    }

    @Override
    public void removeMembershipAnnotation(Membership membership, List<String> tagsToRemove) {
        log.info("removing membership annotation from graph ...");
        removeRelationshipAnnotation(participatingRelationship(membership.getUser().getUid(), GraphEntityType.ACTOR,
                ActorType.INDIVIDUAL, membership.getGroup().getUid(), GraphEntityType.ACTOR, ActorType.GROUP), tagsToRemove);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public void addTaskToGraph(Task task, List<String> assignedUserUids) {
        try {
            DebugUtil.transactionRequired("");
            log.info("adding a task to Grassroot Graph ... {}", task.getUid());

            Event taskEvent = createTaskEvent(task);
            IncomingGraphAction action = wrapEntityCreation(taskEvent);
            addParticipants(action, new HashSet<String>(assignedUserUids), GraphEntityType.ACTOR,
                    ActorType.INDIVIDUAL.name(), task.getUid(), GraphEntityType.EVENT, taskEvent.getEventType().name());
            action.addRelationship(participatingRelationship(task.getUid(), GraphEntityType.EVENT, taskEvent.getEventType().name(),
                    task.getAncestorGroup().getPlatformUid(), GraphEntityType.ACTOR, ActorType.GROUP.name()));
            action.addRelationship(generatorRelationship(task.getCreatedByUser().getPlatformUid(), GraphEntityType.ACTOR,
                    ActorType.INDIVIDUAL.name(), task.getUid(), GraphEntityType.EVENT, taskEvent.getEventType().name()));

            dispatchAction(action, "task");
        } catch (LazyInitializationException e) {
            log.error("Spring-Hibernate hell continues, can't add to graph, Lazy Init as usual");
        }
    }

    @Override
    public void addTaskAnnotation(Task task, String[] tags, String description) {
        log.info("adding task annotation to graph ... {}", task.getUid());

        List<String> tagsList = (tags == null || tags.length == 0) ?
                new ArrayList<>() : new ArrayList<>(Arrays.asList(tags));
        if (description != null) tagsList.add(description); // description to be processed

        addEntityAnnotation(createTaskEvent(task), null, tagsList);
    }

    @Override
    public void removeTaskFromGraph(Task task) {
        log.info("removing task from Grassroot graph ... {}", task.getUid());
        removeEntity(createTaskEvent(task));
    }

    @Override
    public void removeTaskAnnotation(Task task, List<String> tagsToRemove) {
        log.info("removing task annotation from graph ... {}", task.getUid());
        removeEntityAnnotation(createTaskEvent(task), null, tagsToRemove);
    }

    private Event createTaskEvent(Task task) {
        TaskType taskType = task.getTaskType();
        EventType graphEventType = TaskType.TODO.equals(taskType) ? EventType.TODO :
                TaskType.VOTE.equals(taskType) ? EventType.VOTE : EventType.MEETING;
        return new Event(graphEventType, task.getUid(), task.getDeadlineTime().toEpochMilli());
    }

    private IncomingGraphAction wrapEntityCreation(GrassrootGraphEntity entity) {
        IncomingDataObject dataObject = new IncomingDataObject(entity.getEntityType(), entity);
        return new IncomingGraphAction(entity.getPlatformUid(), ActionType.CREATE_ENTITY,
                new ArrayList<>(Collections.singletonList(dataObject)), new ArrayList<>(), new ArrayList<>());
    }

    private void removeEntity(GrassrootGraphEntity entity) {
        IncomingDataObject dataObject = new IncomingDataObject(entity.getEntityType(), entity);
        IncomingGraphAction action = new IncomingGraphAction(entity.getPlatformUid(), ActionType.REMOVE_ENTITY,
                new ArrayList<>(Collections.singletonList(dataObject)), null, null);
        dispatchAction(action, "remove entity");
    }

    private void addParticipants(IncomingGraphAction action, Set<String> participantIds, GraphEntityType participantType,
                                 String participantSubtype, String targetId, GraphEntityType targetType, String targetSubtype) {
        if (participantIds != null && !participantIds.isEmpty()) participantIds.forEach(participantId ->
                action.addRelationship(participatingRelationship(participantId, participantType, participantSubtype,
                        targetId, targetType, targetSubtype)));
    }

    private void addGenerators(IncomingGraphAction action, Set<String> generatorIds, GraphEntityType generatorType,
                               String generatorSubtype, String targetId, GraphEntityType targetType, String targetSubtype) {
        if (generatorIds != null && !generatorIds.isEmpty()) generatorIds.forEach(generatorId ->
                action.addRelationship(generatorRelationship(generatorId, generatorType, generatorSubtype,
                        targetId, targetType, targetSubtype)));
    }

    private IncomingRelationship participatingRelationship(String participantUid, GraphEntityType participantType, String participantSubtype,
                                                           String targetUid, GraphEntityType targetType, String targetSubtype) {
        return new IncomingRelationship(participantUid, participantType, participantSubtype,
                targetUid, targetType, targetSubtype, GrassrootRelationship.Type.PARTICIPATES);
    }

    private IncomingRelationship generatorRelationship(String generatorUid, GraphEntityType generatorType, String generatorSubtype,
                                                       String targetUid, GraphEntityType targetType, String targetSubtype) {
        return new IncomingRelationship(generatorUid, generatorType, generatorSubtype,
                targetUid, targetType, targetSubtype, GrassrootRelationship.Type.GENERATOR);
    }

    private void removeParticipationRelationship(String tailUid, GraphEntityType tailType, String tailSubtype,
                                                 String headUid, GraphEntityType headType, String headSubtype) {
        IncomingRelationship relationship = participatingRelationship(tailUid, tailType, tailSubtype,
                headUid, headType, headSubtype);
        IncomingGraphAction action = new IncomingGraphAction(tailUid, ActionType.REMOVE_RELATIONSHIP,
                null, new ArrayList<>(Collections.singletonList(relationship)), null);
        dispatchAction(action, "remove participation relationship");
    }

    private void addEntityAnnotation(GrassrootGraphEntity entity, Map<String, String> properties, List<String> tags) {
        IncomingDataObject dataObject = new IncomingDataObject(entity.getEntityType(), entity);
        IncomingAnnotation annotation = new IncomingAnnotation(dataObject, null, properties, tags, null);
        IncomingGraphAction graphAction = new IncomingGraphAction(dataObject.getGraphEntity().getPlatformUid(),
                ActionType.ANNOTATE_ENTITY, null, null, new ArrayList<>(Collections.singletonList(annotation)));
        dispatchAction(graphAction, "entity annotation");
    }

    private void removeEntityAnnotation(GrassrootGraphEntity entity, Set<String> keysToRemove, List<String> tags) {
        IncomingDataObject dataObject = new IncomingDataObject(entity.getEntityType(), entity);
        IncomingAnnotation annotation = new IncomingAnnotation(dataObject, null, null, tags, keysToRemove);
        IncomingGraphAction graphAction = new IncomingGraphAction(dataObject.getGraphEntity().getPlatformUid(),
                ActionType.REMOVE_ANNOTATION, null, null, new ArrayList<>(Collections.singletonList(annotation)));
        dispatchAction(graphAction, "remove entity annotation");
    }

    private void addRelationshipAnnotation(IncomingRelationship relationship, List<String> tags) {
        IncomingAnnotation annotation = new IncomingAnnotation(null, relationship, null, tags, null);
        IncomingGraphAction graphAction = new IncomingGraphAction(relationship.getTailEntityPlatformId(),
                ActionType.ANNOTATE_RELATIONSHIP, null, null, new ArrayList<>(Collections.singletonList(annotation)));
        dispatchAction(graphAction, "relationship annotation");
    }

    private void removeRelationshipAnnotation(IncomingRelationship relationship, List<String> tags) {
        IncomingAnnotation annotation = new IncomingAnnotation(null, relationship, null, tags, null);
        IncomingGraphAction graphAction = new IncomingGraphAction(relationship.getTailEntityPlatformId(),
                ActionType.REMOVE_ANNOTATION, null, null, new ArrayList<>(Collections.singletonList(annotation)));
        dispatchAction(graphAction, "remove relationship annotation");
    }

    private void dispatchAction(IncomingGraphAction action, String actionDescription) {
        try {
            log.info("dispatching message to URL: {}", sqsQueueUrl);
            SendMessageRequest request = new SendMessageRequest(sqsQueueUrl, objectMapper.writeValueAsString(action));
            if (isQueueFifo) {
                request.setMessageGroupId("graphCrudActions");
            }
            sqs.sendMessage(request);
            log.info("successfully dispatched {} to graph entity queue ...", actionDescription);
        } catch (JsonProcessingException e) {
            log.error("error adding graph action to queue ... ", e);
        }
    }

}