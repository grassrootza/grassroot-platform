package za.org.grassroot.integration.graph;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.enums.ActorType;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.enums.GrassrootRelationship;
import za.org.grassroot.graph.dto.ActionType;
import za.org.grassroot.graph.dto.IncomingDataObject;
import za.org.grassroot.graph.dto.IncomingGraphAction;
import za.org.grassroot.graph.dto.IncomingRelationship;

import javax.annotation.PostConstruct;
import java.util.Collections;

@Service @Slf4j
@ConditionalOnProperty("grassroot.graph.enabled")
public class GraphBrokerImpl implements GraphBroker {

    private AmazonSQS sqs;
    private ObjectMapper objectMapper;

    @Value("${grassroot.graph.sqs.queue:grassroot-graph-test}")
    private String grassrootQueue;
    private String sqsQueueUrl;

    @Autowired
    public GraphBrokerImpl(ObjectMapper objectMapper) {
        log.info("Graph broker enabled, constructing ...");
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        this.sqs = AmazonSQSClientBuilder.defaultClient();
        this.sqsQueueUrl = sqs.getQueueUrl(grassrootQueue).getQueueUrl();
    }

    @Override
    public void addUserToGraph(String userUid) {
        log.info("adding user to Grassroot graph ... {}", userUid);
        Actor actor = new Actor(ActorType.INDIVIDUAL);
        actor.setPlatformUid(userUid);
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.ACTOR, actor);
        IncomingGraphAction action = new IncomingGraphAction(userUid,
                ActionType.CREATE_ENTITY,
                Collections.singletonList(dataObject),
                Collections.emptyList());
        try {
            sqs.sendMessage(new SendMessageRequest(sqsQueueUrl, objectMapper.writeValueAsString(action)));
            log.info("successfully dispatched new user to graph entity queue ...");
        } catch (JsonProcessingException e) {
            log.error("error adding user to queue ...", e);
        }
    }

    @Override
    public void addGroupToGraph(String groupUid, String creatingUserUid) {
        log.info("adding group to Grassroot graph ...");
        Actor group = new Actor(ActorType.GROUP);
        group.setPlatformUid(groupUid);
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.ACTOR, group);
        IncomingRelationship relationship = new IncomingRelationship(creatingUserUid, GraphEntityType.ACTOR,
                groupUid, GraphEntityType.ACTOR, GrassrootRelationship.Type.GENERATOR);
        IncomingGraphAction action = new IncomingGraphAction(groupUid,
                ActionType.CREATE_ENTITY,
                Collections.singletonList(dataObject),
                Collections.singletonList(relationship));
        try {
            sqs.sendMessage(new SendMessageRequest(sqsQueueUrl, objectMapper.writeValueAsString(action)));
            log.info("successfully dispatched new group to graph entity queue ...");
        } catch (JsonProcessingException e) {
            log.error("error adding group to queue ... ", e);
        }
    }

    @Override
    public void addAccountToGraph(String accountUid) {

    }

    @Override
    public void addMembershipToGraph(String userUid, String groupUid) {

    }

    @Override
    public void removeMembershipFromGraph(String userUid, String groupUid) {

    }

    @Override
    public void addTaskToGraph(String taskUid, TaskType taskType) {

    }
}
