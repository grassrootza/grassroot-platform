package za.org.grassroot.integration.graph;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.task.Task;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.enums.ActorType;
import za.org.grassroot.graph.domain.enums.EventType;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.enums.GrassrootRelationship;
import za.org.grassroot.graph.dto.*;

import javax.annotation.PostConstruct;
import java.util.*;
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

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final EventRepository eventRepository;
    private final TodoRepository todoRepository;

    @Autowired
    public GraphBrokerImpl(UserRepository userRepository, GroupRepository groupRepository, MembershipRepository membershipRepository,
                           EventRepository eventRepository, TodoRepository todoRepository) {
        log.info("Graph broker enabled, constructing object mapper ...");
        this.objectMapper = new ObjectMapper().findAndRegisterModules()
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);

        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.eventRepository = eventRepository;
        this.todoRepository = todoRepository;
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
    public void addGroupToGraph(String groupUid, String creatingUserUid, Set<String> memberUids) {
        log.info("adding group to Grassroot graph ... {}", groupUid);

        IncomingGraphAction action = wrapEntityCreation(new Actor(ActorType.GROUP, groupUid));
        addParticipants(action, memberUids, GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(),
                groupUid, GraphEntityType.ACTOR, ActorType.GROUP.name());
        action.addRelationship(generatorRelationship(creatingUserUid, GraphEntityType.ACTOR,
                ActorType.INDIVIDUAL.name(), groupUid, GraphEntityType.ACTOR, ActorType.GROUP.name()));

        dispatchAction(action, "group");
    }

    @Override
    public void addMovementToGraph(String movementUid, String creatingUserUid) {
        log.info("adding movement to Grassroot graph ...");

        Actor movement = new Actor(ActorType.MOVEMENT, movementUid);
        IncomingGraphAction action = wrapEntityCreation(movement);
        IncomingRelationship genRel = generatorRelationship(creatingUserUid, GraphEntityType.ACTOR,
                ActorType.INDIVIDUAL.name(), movementUid, GraphEntityType.ACTOR, ActorType.MOVEMENT.name());
        action.addRelationship(genRel);

        dispatchAction(action, "movement");
    }

    @Override
    public void addAccountToGraph(String accountUid, List<String> adminUids) {
        log.info("adding account to Grassroot graph ... {}", accountUid);

        IncomingGraphAction action = wrapEntityCreation(new Actor(ActorType.ACCOUNT, accountUid));
        addGenerators(action, new HashSet<>(adminUids), GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(),
                accountUid, GraphEntityType.ACTOR, ActorType.ACCOUNT.name());

        dispatchAction(action, "account");
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
    @Transactional
    @SuppressWarnings("unchecked")
    public void addTaskToGraph(String taskUid, TaskType taskType, List<String> assignedUserUids) {
        DebugUtil.transactionRequired("");
        log.info("adding task to Grassroot Graph ... {}", taskUid);
        Task task = getTask(taskUid, taskType);
        if (task == null) {
            log.error("Error getting task of type {} and uid {}", taskType, taskUid);
            return;
        }
        Event taskEvent = createTaskEvent(task);
        IncomingGraphAction action = wrapEntityCreation(taskEvent);
        addParticipants(action, new HashSet<>(assignedUserUids), GraphEntityType.ACTOR,
                ActorType.INDIVIDUAL.name(), task.getUid(), GraphEntityType.EVENT, taskEvent.getEventType().name());
        action.addRelationship(participatingRelationship(task.getUid(), GraphEntityType.EVENT, taskEvent.getEventType().name(),
                task.getAncestorGroup().getUid(), GraphEntityType.ACTOR, ActorType.GROUP.name()));
        action.addRelationship(generatorRelationship(task.getCreatedByUser().getUid(), GraphEntityType.ACTOR,
                ActorType.INDIVIDUAL.name(), task.getUid(), GraphEntityType.EVENT, taskEvent.getEventType().name()));

        dispatchAction(action, "task");
    }

    @Override
    public void annotateUser(String userUid, Map<String, String> properties, Set<String> tags, boolean setAllAnnotations) {
        log.debug("annotating Grassroot graph user ... {}", userUid);

        if (setAllAnnotations) {
            User user = userRepository.findOneByUid(userUid);
            if (user == null) {
                log.info("Error, user given to graph broker is null, userUid: {}.", userUid);
                return;
            }
            properties = new HashMap<>();
            properties.put(IncomingAnnotation.language, user.getLanguageCode());
            String province = Province.CANONICAL_NAMES_ZA.get(user.getProvince());
            properties.put(IncomingAnnotation.province, province);
        }

        Actor actor = new Actor(ActorType.INDIVIDUAL, userUid);
        annotateEntity(actor, properties, tags);
    }

    @Override
    public void annotateGroup(String groupUid, Map<String, String> properties, Set<String> tags, boolean setAllAnnotations) {
        log.debug("annotating Grassroot graph group ... {}", groupUid);

        if (setAllAnnotations) {
            Group group = groupRepository.findOneByUid(groupUid);
            if (group == null) {
                log.error("Error, user given to graph broker is null, groupUid: {}.", groupUid);
                return;
            }
            properties = new HashMap<>();
            properties.put(IncomingAnnotation.name, group.getName());
            properties.put(IncomingAnnotation.language, group.getDefaultLanguage());
            properties.put(IncomingAnnotation.description, group.getDescription());
            tags = new HashSet<>(group.getTagList());
        }

        Actor actor = new Actor(ActorType.GROUP, groupUid);
        annotateEntity(actor, properties, tags);
    }

    @Override
    public void annotateMembership(String userUid, String groupUid, Set<String> tags, boolean setAllAnnotations) {
        log.info("annotating Grassroot graph membership, user {}, group {} ...", userUid, groupUid);

        if (setAllAnnotations) {
            Membership membership = membershipRepository.findByGroupUidAndUserUid(groupUid, userUid);
            if (membership == null) {
                log.error("Error, user given to graph broker is null, userUid: {}, groupUid: {}.", userUid, groupUid);
                return;
            }
            tags = new HashSet<>(membership.getTagList());
        }

        annotateRelationship(participatingRelationship(userUid, GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(),
                groupUid, GraphEntityType.ACTOR, ActorType.GROUP.name()), tags);
    }

    @Override
    public void annotateTask(String taskUid, TaskType taskType, Map<String, String> properties, Set<String> tags, boolean setAllAnnotations) {
        log.info("annotating Grassroot graph task ... {}", taskUid);

        Task task = getTask(taskUid, taskType);
        if (task == null) {
            log.error("Error, task given to graph broker is null, check if TX is committed, etc. type: {}, uid: {}", taskType, taskUid);
            return;
        }

        if (setAllAnnotations) {
            properties = new HashMap<>();
            properties.put(IncomingAnnotation.name, task.getName());
            properties.put(IncomingAnnotation.description, task.getDescription());
            tags = new HashSet<>(task.getTagList());
        }

        Event event = createTaskEvent(task);
        annotateEntity(event, properties, tags);
    }

    @Override
    public void removeUserFromGraph(String userUid) {
        log.info("removing user from Grassroot graph ... {}", userUid);
        removeEntityFromGraph(new Actor(ActorType.INDIVIDUAL, userUid));
    }

    @Override
    public void removeGroupFromGraph(String groupUid) {
        log.info("removing group from Grassroot graph ... {}", groupUid);
        removeEntityFromGraph(new Actor(ActorType.GROUP, groupUid));
    }

    @Override
    public void removeAccountFromGraph(String accountUid) {
        log.info("removing account from Grassroot graph ... {}", accountUid);
        removeEntityFromGraph(new Actor(ActorType.ACCOUNT, accountUid));
    }

    @Override
    public void removeMembershipFromGraph(String userUid, String groupUid) {
        log.info("removing membership from Grassroot graph, user {}, group {} ...", userUid, groupUid);
        removeParticipationRelationship(userUid, GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(),
                groupUid, GraphEntityType.ACTOR, ActorType.GROUP.name());
    }

    @Override
    public void removeTaskFromGraph(String taskUid, TaskType taskType) {
        log.info("removing task from Grassroot graph ... {}", taskUid);
        Task task = getTask(taskUid, taskType);
        if (task != null) {
            removeEntityFromGraph(createTaskEvent(task));
        }
    }

    @Override
    public void removeAnnotationsFromUser(String userUid, Set<String> keysToRemove, Set<String> tagsToRemove) {
        log.info("removing annotations from Grassroot graph user ... {}", userUid);
        removeAnnotationsFromEntity(new Actor(ActorType.INDIVIDUAL, userUid), keysToRemove, tagsToRemove);
    }

    @Override
    public void removeAnnotationsFromGroup(String groupUid, Set<String> keysToRemove, Set<String> tagsToRemove) {
        log.info("removing annotations from Grassroot graph group ... {}", groupUid);
        removeAnnotationsFromEntity(new Actor(ActorType.GROUP, groupUid), keysToRemove, tagsToRemove);
    }

    @Override
    public void removeAnnotationsFromMembership(String userUid, String groupUid, Set<String> tagsToRemove) {
        log.info("removing annotations from Grassroot graph membership, user {}, group {} ...", userUid, groupUid);
        removeAnnotationsFromRelationship(participatingRelationship(userUid, GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(),
                groupUid, GraphEntityType.ACTOR, ActorType.GROUP.name()), tagsToRemove);
    }

    @Override
    public void removeAnnotationsFromTask(String taskUid, TaskType taskType, Set<String> keysToRemove, Set<String> tagsToRemove) {
        log.info("removing annotations from Grassroot graph task ... {}", taskUid);
        removeAnnotationsFromEntity(createTaskEvent(getTask(taskUid, taskType)), keysToRemove, tagsToRemove);
    }

    private void removeEntityFromGraph(GrassrootGraphEntity entity) {
        IncomingDataObject dataObject = new IncomingDataObject(entity.getEntityType(), entity);
        IncomingGraphAction action = new IncomingGraphAction(entity.getPlatformUid(), ActionType.REMOVE_ENTITY,
                new ArrayList<>(Collections.singletonList(dataObject)), null, null);
        dispatchAction(action, "remove entity");
    }

    private void removeParticipationRelationship(String tailUid, GraphEntityType tailType, String tailSubtype,
                                                 String headUid, GraphEntityType headType, String headSubtype) {
        IncomingRelationship relationship = participatingRelationship(tailUid, tailType, tailSubtype, headUid, headType, headSubtype);
        IncomingGraphAction action = new IncomingGraphAction(tailUid, ActionType.REMOVE_RELATIONSHIP,
                null, new ArrayList<>(Collections.singletonList(relationship)), null);
        dispatchAction(action, "remove participation relationship");
    }

    private void annotateEntity(GrassrootGraphEntity entity, Map<String, String> properties, Set<String> tags) {
        IncomingDataObject dataObject = new IncomingDataObject(entity.getEntityType(), entity);
        IncomingAnnotation annotation = new IncomingAnnotation(dataObject, null, normalize(properties), normalize(tags), null);
        IncomingGraphAction action = new IncomingGraphAction(dataObject.getGraphEntity().getPlatformUid(),
                ActionType.ANNOTATE_ENTITY, null, null, Collections.singletonList(annotation));
        dispatchAction(action, "annotate entity");
    }

    private void removeAnnotationsFromEntity(GrassrootGraphEntity entity, Set<String> keysToRemove, Set<String> tagsToRemove) {
        IncomingDataObject dataObject = new IncomingDataObject(entity.getEntityType(), entity);
        IncomingAnnotation annotation = new IncomingAnnotation(dataObject, null, null, normalize(tagsToRemove), normalize(keysToRemove));
        IncomingGraphAction action = new IncomingGraphAction(dataObject.getGraphEntity().getPlatformUid(),
                ActionType.REMOVE_ANNOTATION, null, null, Collections.singletonList(annotation));
        dispatchAction(action, "remove annotations from entity");
    }

    private void annotateRelationship(IncomingRelationship relationship, Set<String> tags) {
        IncomingAnnotation annotation = new IncomingAnnotation(null, relationship, null, normalize(tags), null);
        IncomingGraphAction action = new IncomingGraphAction(relationship.getTailEntityPlatformId(),
                ActionType.ANNOTATE_RELATIONSHIP, null, null, Collections.singletonList(annotation));
        dispatchAction(action, "annotate relationship");
    }

    private void removeAnnotationsFromRelationship(IncomingRelationship relationship, Set<String> tagsToRemove) {
        IncomingAnnotation annotation = new IncomingAnnotation(null, relationship, null, normalize(tagsToRemove), null);
        IncomingGraphAction action = new IncomingGraphAction(relationship.getTailEntityPlatformId(),
                ActionType.REMOVE_ANNOTATION, null, null, Collections.singletonList(annotation));
        dispatchAction(action, "remove annotations from relationship");
    }

    private void dispatchAction(IncomingGraphAction action, String actionDescription) {
        try {
            log.debug("dispatching message to URL: {}", sqsQueueUrl);
            SendMessageRequest request = new SendMessageRequest(sqsQueueUrl, objectMapper.writeValueAsString(action));
            if (isQueueFifo) {
                request.setMessageGroupId("graphCrudActions");
            }
            sqs.sendMessage(request);
            log.debug("successfully dispatched {} to graph entity queue ...", actionDescription);
        } catch (JsonProcessingException e) {
            log.error("error adding graph action to queue ... ", e);
        }
    }

    private IncomingGraphAction wrapEntityCreation(GrassrootGraphEntity entity) {
        IncomingDataObject dataObject = new IncomingDataObject(entity.getEntityType(), entity);
        return new IncomingGraphAction(entity.getPlatformUid(), ActionType.CREATE_ENTITY,
                new ArrayList<>(Collections.singletonList(dataObject)), new ArrayList<>(), new ArrayList<>());
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

    private Event createTaskEvent(Task task) {
        TaskType taskType = task.getTaskType();
        EventType graphEventType = TaskType.TODO.equals(taskType) ? EventType.TODO :
                TaskType.VOTE.equals(taskType) ? EventType.VOTE : EventType.MEETING;
        return new Event(graphEventType, task.getUid(), task.getDeadlineTime().toEpochMilli());
    }

    private Task getTask(String taskUid, TaskType taskType) {
        switch (taskType) {
            case MEETING:   return eventRepository.findOneByUid(taskUid);
            case VOTE:      return eventRepository.findOneByUid(taskUid);
            case TODO:      return todoRepository.findOneByUid(taskUid);
        }
        return null;
    }

    private Set<String> normalize(Set<String> set) {
        return set == null ? new HashSet<>() : set.stream()
                .filter(s -> !StringUtils.isEmpty(s))
                .map(String::toLowerCase).map(String::trim).collect(Collectors.toSet());
    }

    private Map<String, String> normalize(Map<String, String> map) {
        return map == null ? new HashMap<>() : map.entrySet().stream()
                .filter(entry -> !StringUtils.isEmpty(entry.getValue()))
                .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase().trim(),
                        entry -> entry.getValue().toLowerCase().trim()));
    }

}