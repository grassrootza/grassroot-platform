package za.org.grassroot.integration.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.domain.SeloApiCallFailure;
import za.org.grassroot.integration.domain.SeloParseDateTimeFailure;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Created by shakka on 8/15/16.
 */
@Service
public class LearningManager implements LearningService {

    private Logger log = LoggerFactory.getLogger(LearningManager.class);

    private static final String ERROR_PARSING = "ERROR_PARSING";
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;


    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private Environment environment;

    private String learningIP;

    @PostConstruct
    public void init() {
        learningIP = environment.getProperty("LEARNING_IP", "127.0.0.1"); // default to localhost if not set
    }

    @Override
    public LocalDateTime parse(String phrase) {

        LocalDateTime parsedDateTime;

        try {
            // note : the rest template is autowired to use a default character encoding (UTF 8), so putting encode
            // here will double encode and throw errors, hence leave it out
            String url = UriComponentsBuilder
                    .fromHttpUrl(learningIP)
                    .path("parse")
                    .queryParam("phrase", phrase)
                    .build()
                    .toUriString();

            long start = System.currentTimeMillis();
            String s = this.restTemplate.getForObject(url, String.class);
            if (ERROR_PARSING.equals(s)) {
                // throw error so can tell user didn't understand, preferable to returning current date time
                throw new SeloParseDateTimeFailure();
            } else {
                parsedDateTime = LocalDateTime.parse(s, dateTimeFormatter); // might do via an incoming binder, but would just perform same operation
                log.info("Time to process: {} msecs, returning : ", System.currentTimeMillis() - start, parsedDateTime.toString());
                return parsedDateTime;
            }
        } catch (SeloParseDateTimeFailure e) {
            throw e;
        } catch (Exception e) {
            // throw an error because this shouldn't happen (might be because of an error reaching the server ...)
            log.error("Error calling Selo! Error message: {}", e.toString());
            throw new SeloApiCallFailure();
        }
    }


}
