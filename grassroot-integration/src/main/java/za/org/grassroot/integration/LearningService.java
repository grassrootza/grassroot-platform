package za.org.grassroot.integration;

import com.google.protobuf.ByteString;
import za.org.grassroot.integration.exception.SeloParseDateTimeFailure;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Created by shakka on 8/15/16.
 */
public interface LearningService {

   LocalDateTime parse(String phrase) throws SeloParseDateTimeFailure;

   Map<String, Double> findRelatedTerms(String searchTerm);

   String speechToText(ByteString rawSpeech);
}
