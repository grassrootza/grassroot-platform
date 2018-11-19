package za.org.grassroot.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.integration.exception.SeloApiCallFailure;
import za.org.grassroot.integration.exception.SeloParseDateTimeFailure;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Created by shakka on 8/15/16.
 */
@Service @Slf4j
public class LearningManager implements LearningService {

    @Value("${grassroot.datetime.lambda.url:http://localhost:5000/parse}")
    private String learningLambdaUrl;

    @Value("${grassroot.datetime.poll.datetime:false}")
    private boolean pollLearning;

    private static final String ERROR_PARSING = "ERROR_PARSING";
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"); //01-11-2018T00:00

    private RestTemplate restTemplate;

    @Autowired
    public LearningManager(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public LocalDateTime parse(String phrase) {

        LocalDateTime parsedDateTime;

        try {
            // note : the rest template is autowired to use a default character encoding (UTF 8), so putting encode
            // here will double encode and throw errors, hence leave it out
            String url = UriComponentsBuilder.fromHttpUrl(learningLambdaUrl)
                    .queryParam("date_string", phrase)
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

    @Scheduled(cron = "0 0/5 5-19 * * ?")
    public void keepAliveParser() {
        if (pollLearning) {
            log.info("Keeping warm date time parser ... ");
            try {
                URI uri = UriComponentsBuilder.fromHttpUrl(learningLambdaUrl).queryParam("date_string", "Tomorrow at 9am").build().toUri();
                String pollingResult = restTemplate.getForObject(uri, String.class);
                log.info("Polling date time returned: {}", pollingResult);
            } catch (RestClientException e) {
                log.error("Keep alive error: {}", e.getMessage());
            }
        }
    }

}
