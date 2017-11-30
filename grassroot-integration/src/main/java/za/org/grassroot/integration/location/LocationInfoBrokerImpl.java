package za.org.grassroot.integration.location;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// todo : use JWT? though, these are open datasets (similar q on http vs https, esp if these are in same VPC)
@Component @Slf4j
@ConditionalOnProperty(name = "grassroot.geo.apis.enabled", matchIfMissing = false)
public class LocationInfoBrokerImpl implements LocationInfoBroker {

    private final Environment environment;
    private final RestTemplate restTemplate;

    private String geoApiHost;
    private Integer geoApiPort;

    @Autowired
    public LocationInfoBrokerImpl(Environment environment, RestTemplate restTemplate) {
        this.environment = environment;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() {
        log.info("GeoAPI integration is active, setting up URLs etc");
        geoApiHost = environment.getProperty("grassroot.geo.apis.host", "localhost");
        geoApiPort = environment.getProperty("grassroot.geo.apis.port", Integer.class, 80);
    }

    @Override
    public List<ProvinceSA> getAvailableProvincesForDataSet(String dataSetLabel) {
        return null;
    }

    @Override
    public List<Locale> getAvailableLocalesForDataSet(String dataSetLabel) {
        return null;
    }

    @Override
    public List<String> getAvailableInfoForProvince(String dataSetLabel, ProvinceSA province, Locale locale) {
        URI uriToCall = UriComponentsBuilder.newInstance()
                .scheme("http")
                .host(geoApiHost)
                .port(geoApiPort)
                .path("/sets/available/{dataset}")
                .queryParam("province", province.toString())
                .queryParam("locale", locale.toLanguageTag())
                .buildAndExpand(dataSetLabel).toUri();

        log.info("assembled URI string to get list of data sets = {}", uriToCall.toString());

        return getResult(uriToCall);
    }

    @Override
    public List<String> retrieveRecordsForProvince(String dataSetLabel, String infoSetTag, ProvinceSA province, Locale locale) {
        URI uriToCall = UriComponentsBuilder.newInstance()
                .scheme("http")
                .host(geoApiHost)
                .port(geoApiPort)
                .path("/records/{dataset}/{infoSet}")
                .queryParam("province", province.toString())
                .queryParam("locale", locale.toLanguageTag())
                .buildAndExpand(dataSetLabel, infoSetTag).toUri();

        log.info("assembled URI to get records = {}", uriToCall.toString());

        return getResult(uriToCall);
    }

    private List<String> getResult(URI uri) {
        try {
            ResponseEntity<String[]> availableInfo = restTemplate.getForEntity(uri, String[].class);
            return Arrays.asList(availableInfo.getBody());
        } catch (RestClientException e) {
            log.error("error calling geo API!", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void assembleAndSendRecordMessage(String dataSetLabel, String infoSetTag, ProvinceSA province, Locale locale, String sponsoringAccountUid) {
        // todo : assemble and send priority SMS
    }
}
