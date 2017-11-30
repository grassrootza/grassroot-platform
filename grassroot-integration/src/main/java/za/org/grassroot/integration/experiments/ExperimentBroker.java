package za.org.grassroot.integration.experiments;

import java.util.Map;

public interface ExperimentBroker {

    void initiateExperiment(String experimentKey);

    VariationAssignment assignUser(String experimentKey, String userUid, Map<String, String> attributes);

    void recordEvent(String eventKey, String userUid, Map<String, String> attributes, Map<String, Object> events);

}