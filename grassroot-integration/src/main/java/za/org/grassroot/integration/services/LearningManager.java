package za.org.grassroot.integration.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Created by shakka on 8/15/16.
 */
@Service
public class LearningManager implements LearningService {

    private Logger log = LoggerFactory.getLogger(LearningManager.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private Environment environment;

    private String learningIP;

    @PostConstruct
    public void init() {
        learningIP = environment.getProperty("LEARNING_IP", learningIP);
    }

    @Override
    public LocalDateTime parse(String phrase) {

        LocalDateTime parsedDateTime;

        try {
            // if not covered    in next version of Jackson, use property binder

            String url = learningIP + phrase;
            long start = System.currentTimeMillis();
            // RestTemplate rt = new RestTemplate();
            String s = this.restTemplate.getForObject(url, String.class);
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            parsedDateTime = LocalDateTime.parse(s, dateTimeFormatter);
            log.info("Time to process: {}", System.currentTimeMillis() - start);
            log.info("Date time processed: " + parsedDateTime.toString());
            return parsedDateTime;
        } catch (Exception e) {
            throw e;
        }
    }


}
