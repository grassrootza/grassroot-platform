package za.org.grassroot.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.integration.exception.SeloApiCallFailure;
import za.org.grassroot.integration.exception.SeloParseDateTimeFailure;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by shakka on 8/15/16.
 */
@Service @Slf4j
public class LearningManager implements LearningService {

    private static final String ERROR_PARSING = "ERROR_PARSING";
    private static final String POLLING_STRING = "Tomorrow at 9am";
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"); //01-11-2018T00:00

    private RestTemplate restTemplate;
    private Environment environment;

    private String learningHost;
    private int learningPort;
    private String dateTimePath;
    private String dateTimeParam;
    private String relatedTermPath;
    private String relatedTermParam;
    private boolean pollLearning;

    @Autowired
    public LearningManager(RestTemplate restTemplate, Environment environment) {
        this.restTemplate = restTemplate;
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        learningHost = environment.getProperty("grassroot.learning.host", "learning.grassroot.cloud"); // default to localhost if not set
        learningPort = environment.getProperty("grassroot.learning.port", Integer.class, 80);
        dateTimePath = environment.getProperty("grassroot.learning.datetime.path", "datetime");
        dateTimeParam = environment.getProperty("grassroot.learning.datetime.param", "date_string");
        relatedTermPath = environment.getProperty("grassroot.learning.relatedterm.path", "distance");
        relatedTermParam = environment.getProperty("grassroot.learning.relatedterm.param", "text");
        pollLearning = environment.getProperty("grassroot.learning.poll", Boolean.class, false);
    }

    @Scheduled(cron = "0 0/5 5-19 * * ?")
    public void keepAliveParser() {
        if (pollLearning) {
            log.info("Keeping warm date time parser ... ");
            try {
                String pollingResult = restTemplate.getForObject(UriComponentsBuilder.newInstance().scheme("http").host(learningHost).port(learningPort)
                        .path(dateTimePath).queryParam(dateTimeParam, POLLING_STRING).build().toUri(), String.class);
                log.info("Polling date time returned: {}", pollingResult);
            } catch (RestClientException e) {
                log.error("Keep alive error: {}", e.getMessage());
            }
        }
    }

    @Override
    public LocalDateTime parse(String phrase) {

        LocalDateTime parsedDateTime;

        try {
            // note : the rest template is autowired to use a default character encoding (UTF 8), so putting encode
            // here will double encode and throw errors, hence leave it out
            String url = UriComponentsBuilder.newInstance()
                    .scheme("http")
                    .host(learningHost)
                    .port(learningPort)
                    .path(dateTimePath)
                    .queryParam(dateTimeParam, phrase)
                    .build()
                    .toUriString();

            log.info("Attempting to parse date time with url: {}", url);

            long start = System.currentTimeMillis();
            String s = this.restTemplate.getForObject(url, String.class);
            if (ERROR_PARSING.equals(s)) {
                // throw error so can tell user didn't understand, preferable to returning current date time
                throw new SeloParseDateTimeFailure();
            } else {
                parsedDateTime = LocalDateTime.parse(s, dateTimeFormatter); // might do via an incoming binder, but would just perform same operation
                log.info("Time to process: {} msecs, returning : {}", System.currentTimeMillis() - start, parsedDateTime.toString());
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

    @Override
    public Map<String, Double> findRelatedTerms(String searchTerm) {
        try {
            String url = UriComponentsBuilder.newInstance()
                    .scheme("http")
                    .host(learningHost)
                    .port(learningPort)
                    .path(relatedTermPath)
                    .queryParam(relatedTermParam, searchTerm)
                    .build()
                    .toString();

            log.info("Calling learning service, with URL: {}", url);

            @SuppressWarnings("unchecked")
            Map<String, Double> returnedTerms = restTemplate.getForObject(url, HashMap.class);
            return returnedTerms == null ? new HashMap<>() : returnedTerms;
        } catch (ResourceAccessException|HttpStatusCodeException e) {
            log.warn("Error calling learning service! Error: " + e.toString());
            return new HashMap<>();
        } catch (RestClientException e) {
            log.error("Need to fix up the content headers", e);
            return new HashMap<>();
        }
    }

}
