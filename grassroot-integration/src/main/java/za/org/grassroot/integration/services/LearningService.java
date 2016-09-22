package za.org.grassroot.integration.services;

import za.org.grassroot.integration.domain.SeloParseDateTimeFailure;

import java.time.LocalDateTime;

/**
 * Created by shakka on 8/15/16.
 */
public interface LearningService {

   LocalDateTime parse(String phrase) throws SeloParseDateTimeFailure;
}
