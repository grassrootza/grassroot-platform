package za.org.grassroot.integration.location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

/**
 * Created by luke on 2017/04/24.
 */
@Configuration
@ConditionalOnProperty(name = "ussd.location.service", havingValue = "aat_soap", matchIfMissing = false)
public class AatLocationSoapConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AatLocationSoapConfiguration.class);

    @Value("${aat.lbs.soap.url:http://localhost:8080}")
    private String aatSoapUrl;

    @Value("${aat.lbs.soap.schema:aatschema.wdsl}")
    private String aatSchema;

    @Bean
    public Jaxb2Marshaller marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPaths(aatSchema);
        return marshaller;
    }

    @Bean
    public AatSoapClient aatSoapClient() {
        AatSoapClient client = new AatSoapClient();
        client.setDefaultUri(aatSoapUrl);
        client.setMarshaller(marshaller());
        client.setUnmarshaller(marshaller());
        return client;
    }

}
