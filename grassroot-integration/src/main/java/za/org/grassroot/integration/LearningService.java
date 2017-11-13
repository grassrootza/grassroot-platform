package za.org.grassroot.integration;

import za.org.grassroot.integration.exception.SeloParseDateTimeFailure;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Created by shakka on 8/15/16.
 * todo : migrate to nlu broker in time
 */
public interface LearningService {

   LocalDateTime parse(String phrase) throws SeloParseDateTimeFailure;

   Map<String, Double> findRelatedTerms(String searchTerm);


}
