package za.org.grassroot.integration.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service @Slf4j
public class GrassrootIntegrationServicesConfig implements CommandLineRunner {

    private static final String REFRESH_PATH = "/jwt/public/refresh/trusted";

    @Value("${grassroot.messaging.service.url:http://localhost}")
    private String messagingServiceUrl;
    @Value("${grassroot.messaging.service.port:8081}")
    private Integer messagingServicePort;

    @Value("${grassroot.integration.service.url:http://localhost}")
    private String integrationServiceUrl;
    @Value("${grassroot.integration.service.port:8085}")
    private Integer integrationServicePort;

    private final AsyncRestTemplate asyncRestTemplate;

    public GrassrootIntegrationServicesConfig(AsyncRestTemplate asyncRestTemplate) {
        this.asyncRestTemplate = asyncRestTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        // note: these do not tell the corresponding service to trust _this_ server necessarily, which would be an obvious
        // vulnerability, but instead tell it to refresh the keys it has stored for the services that it is wired to trust
        log.info("HTTP servlet booted, telling messaging server to refresh keys");
        asyncRestTemplate.getForEntity(UriComponentsBuilder.fromUriString(messagingServiceUrl).port(messagingServicePort)
                .path(REFRESH_PATH).toUriString(), Boolean.class);
        log.info("HTTP servlet booted, telling integration server to refresh keys");
        asyncRestTemplate.getForEntity(UriComponentsBuilder.fromUriString(integrationServiceUrl).port(messagingServicePort)
                .path(REFRESH_PATH).toUriString(), Boolean.class);
    }
}
