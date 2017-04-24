package za.org.grassroot.integration.location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import za.org.grassroot.integration.location.aatmodels.*;

/**
 * Created by luke on 2017/04/24.
 */

public class AatSoapClient extends WebServiceGatewaySupport {

    private static final Logger logger = LoggerFactory.getLogger(AatSoapClient.class);

    @Value("${grassroot.aat.lbs.username:grassroot}")
    private String aatLbsUsername;

    @Value("${grassroot.aat.lbs.password:password}")
    private String aatLbsPassword;

    public AllowedMsisdnResponse addAllowedMsisdn(final String msisdn, final int permissionType) {
        return (AllowedMsisdnResponse) getWebServiceTemplate()
                .marshalSendAndReceive(new AatDummyRequestObject());
    }

    public RemoveAllowedMsisdnResponse removeAllowedMsisdn(final String msisdn) {
        return (RemoveAllowedMsisdnResponse) getWebServiceTemplate()
                .marshalSendAndReceive(new AatDummyRequestObject());
    }

    public QueryAllowedMsisdnResponse queryAllowedMsisdnResponse(final String msisdn) {
        return (QueryAllowedMsisdnResponse) getWebServiceTemplate()
                .marshalSendAndReceive(new AatDummyRequestObject());
    }

    public GetLocationResponse getLocationResponse(final String msisdn) {
        return (GetLocationResponse) getWebServiceTemplate()
                .marshalSendAndReceive(new AatDummyRequestObject());
    }

}
