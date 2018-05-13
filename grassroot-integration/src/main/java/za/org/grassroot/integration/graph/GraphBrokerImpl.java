package za.org.grassroot.integration.graph;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.enums.ActorType;

@Service @Slf4j
public class GraphBrokerImpl implements GraphBroker {

    private AmazonSQS sqs;
    private ObjectMapper objectMapper;

    @Value("${grassroot.graph.sqs.queue:grassroot-graph-queue}")
    private String grassrootQueue;

    @Autowired
    public GraphBrokerImpl(ObjectMapper objectMapper) {
        this.sqs = AmazonSQSClientBuilder.defaultClient();
        this.objectMapper = objectMapper;
    }

    @Override
    public void addUserToGraph(String userUid) {
        log.info("adding user to Grassroot graph ... {}", userUid);
        Actor actor = new Actor(ActorType.INDIVIDUAL);
        actor.setPlatformUid(userUid);
        try {
            sqs.sendMessage(new SendMessageRequest(grassrootQueue, objectMapper.writeValueAsString(actor)));
        } catch (JsonProcessingException e) {
            log.error("error adding user to queue ...");
        }
    }

    @Override
    public void addGroupToGraph(String groupUid) {

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
