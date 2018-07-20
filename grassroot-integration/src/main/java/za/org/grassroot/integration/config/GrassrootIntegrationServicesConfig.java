package za.org.grassroot.integration.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service @Slf4j
public class GrassrootIntegrationServicesConfig implements CommandLineRunner {

    private static final String REFRESH_PATH = "/jwt/public/refresh/trusted";

    @Value("${grassroot.messaging.service.url:http://localhost}")
    private String messagingServiceUrl;
    @Value("${grassroot.messaging.service.port:8081}")
    private Integer messagingServicePort;

    @Override
    public void run(String... args) throws Exception {
        // note: these do not tell the corresponding service to trust _this_ server necessarily, which would be an obvious
        // vulnerability, but instead tell it to refresh the keys it has stored for the services that it is wired to trust
        final String messageServerInstruction = UriComponentsBuilder.fromUriString(messagingServiceUrl).port(messagingServicePort)
                .path(REFRESH_PATH).toUriString();
        log.info("HTTP servlet booted, telling messaging server to refresh keys, URL: {}", messageServerInstruction);
        WebClient.create(messageServerInstruction)
                .get()
                .retrieve()
                .bodyToMono(Boolean.class)
                .log()
                .subscribe();
    }
}
