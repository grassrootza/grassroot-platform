package za.org.grassroot.integration;

import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URISyntaxException;

/**
 * Created by luke on 2017/06/26.
 */
@Service
public class UrlShortenerImpl implements UrlShortener {

    private static final Logger logger = LoggerFactory.getLogger(UrlShortenerImpl.class);

    @Value("${grassroot.shortener.images.host:https://s3.aws.com/")
    private String longHostUrl;
    @Value("${grassroot.shortener.api.url:https://bitly.com}")
    private String shortenerApi;
    @Value("@{grassroot.shortener.api.key:thisismykey}")
    private String shortenerKey;

    private final RestTemplate restTemplate;

    @Autowired
    public UrlShortenerImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String shortenImageUrl(String bucket, String imageUrl) {
        try {
            String longUrl = longHostUrl + bucket + imageUrl;
            URIBuilder builder = new URIBuilder(shortenerApi)
                    .addParameter("access_token", shortenerKey)
                    .addParameter("longUrl", longUrl);
            BitlyResponse response = restTemplate.getForObject(builder.build(), BitlyResponse.class);
            logger.info("response from Bitly: {}", response);
            return response.url;
        } catch (URISyntaxException e) {
            logger.error("Error shortening URL!", e);
            return null;
        }

    }

    private class BitlyResponse {
        String global_hash;
        String hash;
        String long_url;
        String new_hash;
        String url;

        public String getGlobal_hash() {
            return global_hash;
        }

        public void setGlobal_hash(String global_hash) {
            this.global_hash = global_hash;
        }

        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }

        public String getLong_url() {
            return long_url;
        }

        public void setLong_url(String long_url) {
            this.long_url = long_url;
        }

        public String getNew_hash() {
            return new_hash;
        }

        public void setNew_hash(String new_hash) {
            this.new_hash = new_hash;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        @Override
        public String toString() {
            return "BitlyResponse{" +
                    "long_url='" + long_url + '\'' +
                    ", url='" + url + '\'' +
                    '}';
        }
    }

}
