package za.org.grassroot.integration;

import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URISyntaxException;
import java.util.Map;

/**
 * Created by luke on 2017/06/26.
 */
@Service
public class UrlShortenerImpl implements UrlShortener {

    private static final Logger logger = LoggerFactory.getLogger(UrlShortenerImpl.class);

    @Value("${grassroot.task.images.view.url:http://localhost:8080/image}")
    private String imageViewUrl;

    @Value("${grassroot.shortener.images.host:https://s3.aws.com/")
    private String longHostUrl;
    @Value("${grassroot.shortener.api.url:https://bitly.com}")
    private String shortenerApi;
    @Value("${grassroot.shortener.api.key:thisismykey}")
    private String shortenerKey;

    private final RestTemplate restTemplate;

    @Autowired
    public UrlShortenerImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String shortenImageUrl(String bucket, String imageUrl) {
        try {
            String longUrl = imageViewUrl + "/" + bucket + "/" + imageUrl;
            logger.info("encoding image view URL: {}", longUrl);
            URIBuilder builder = new URIBuilder(shortenerApi)
                    .addParameter("access_token", shortenerKey)
                    .addParameter("longUrl", longUrl);
            BitlyResponse response = restTemplate.getForObject(builder.build(), BitlyResponse.class);
            logger.info("response from Bitly: {}", response);
            return (String) response.data.get("url");
        } catch (URISyntaxException e) {
            logger.error("Error shortening URL!", e);
            return null;
        }

    }

    private static class BitlyResponse {
        String status_code;
        String status_txt;
        Map<String, Object> data;

        public BitlyResponse() {
            // for Jackson
        }

        public String getStatus_code() {
            return status_code;
        }

        public String getStatus_txt() {
            return status_txt;
        }

        public Map<String, Object> getData() {
            return data;
        }

        @Override
        public String toString() {
            return "BitlyResponse{" +
                    "status_code='" + status_code + '\'' +
                    ", status_txt='" + status_txt + '\'' +
                    ", data=" + data +
                    '}';
        }
    }

}
