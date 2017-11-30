package za.org.grassroot.integration.experiments;

import com.optimizely.ab.Optimizely;
import com.optimizely.ab.config.Variation;
import com.optimizely.ab.config.parser.ConfigParseException;
import com.optimizely.ab.event.AsyncEventHandler;
import com.optimizely.ab.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

@Service
public class ExperimentBrokerImpl implements ExperimentBroker, CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ExperimentBrokerImpl.class);

    // should probably do as no-code
    @Value("${grassroot.optimizely.active:false}")
    private boolean optimizelyActive;

    @Value("${grassroot.optimizely.datafile:https://cdn.optimizely.com/json/12345.json}")
    private String dataFileUrl;

    private final RestTemplate restTemplate;
    private Optimizely optimizely;

    public ExperimentBrokerImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void run(String... args) throws Exception {
        if (optimizelyActive) {
            try {
                logger.info("Trying to set up Optimizely client");
                EventHandler eventHandler = new AsyncEventHandler(20000, 1); /// buffer for 20,000 events, 1 thread
                ResponseEntity<String> dataFileResponse = restTemplate.getForEntity(dataFileUrl, String.class);
                optimizely = Optimizely.builder(dataFileResponse.getBody(), eventHandler).build();
            } catch (ConfigParseException e) {
                logger.error("Error! Cannot find Optimizely datafile", e);
            }
        }
    }

    @Override
    public void initiateExperiment(String experimentKey) {
        logger.error("should not be initiating experiment since this is not built ...");
    }

    @Override
    public VariationAssignment assignUser(String experimentKey, String userUid, Map<String, String> attributes) {
        if (optimizely != null) {
            Variation variation = attributes == null ? optimizely.activate(experimentKey, userUid)
                    : optimizely.activate(experimentKey, userUid);
            logger.info("retrieved user variation, looks like: {}", variation);
            return variation == null ? VariationAssignment.UNASSIGNED :
                    variation.is("control") ? VariationAssignment.CONTROL :
                            variation.is("treatment") ? VariationAssignment.EXPERIMENT : VariationAssignment.UNASSIGNED;
        } else {
            return null;
        }
    }

    @Async
    @Override
    public void recordEvent(String eventKey, String userUid, Map<String, String> attributes, Map<String, Object> tags) {
        if (optimizely != null) {
            logger.info("recording AB event, with key: {}", eventKey);
            optimizely.track(eventKey, userUid, attributes == null ? Collections.EMPTY_MAP : attributes,
                    tags == null ? Collections.EMPTY_MAP : tags);
        }
    }

}
