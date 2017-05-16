package za.org.grassroot.integration.location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.soap.SoapMessage;
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



    public AddAllowedMsisdnResponse addAllowedMsisdn(final String msisdn, final int permissionType) {
        AddAllowedMsisdn addAllowedMsisdn = new AddAllowedMsisdn();
        addAllowedMsisdn.setMsisdn(msisdn);
        addAllowedMsisdn.setPermissionType(permissionType);
        addAllowedMsisdn.setUsername(aatLbsUsername);
        addAllowedMsisdn.setPassword(aatLbsPassword);

        logger.info("Sending add allowed msisdn request: {}", addAllowedMsisdn);
        return (AddAllowedMsisdnResponse) getWebServiceTemplate().marshalSendAndReceive(addAllowedMsisdn,
                        message -> ((SoapMessage) message).setSoapAction("http://lbs.gsm.co.za/AddAllowedMsisdn"));
    }

    public AddAllowedMsisdn2Response addAllowedMsisdn2(final String msisdn, final int permissionType) {
        AddAllowedMsisdn2 addAllowedMsisdn2 = new AddAllowedMsisdn2();
        addAllowedMsisdn2.setMsisdn(msisdn);
        addAllowedMsisdn2.setPermissionType(permissionType);
        addAllowedMsisdn2.setUsername(aatLbsUsername);
        addAllowedMsisdn2.setPassword(aatLbsPassword);

        logger.info("Sending add allowed msisdn request: {}", addAllowedMsisdn2);
        return (AddAllowedMsisdn2Response) getWebServiceTemplate().marshalSendAndReceive(addAllowedMsisdn2,
                message -> ((SoapMessage) message).setSoapAction("http://lbs.gsm.co.za/AddAllowedMsisdn2"));
    }

    public RemoveAllowedMsisdnResponse removeAllowedMsisdn(final String msisdn) {
        RemoveAllowedMsisdn removeAllowedMsisdn = new RemoveAllowedMsisdn();
        removeAllowedMsisdn.setMsisdn(msisdn);
        removeAllowedMsisdn.setUsername(aatLbsUsername);
        removeAllowedMsisdn.setPassword(aatLbsPassword);
        return (RemoveAllowedMsisdnResponse) getWebServiceTemplate().marshalSendAndReceive(removeAllowedMsisdn,
                message -> ((SoapMessage) message).setSoapAction("http://lbs.gsm.co.za/RemoveAllowedMsisdn"));
    }

    public QueryAllowedMsisdnResponse queryAllowedMsisdnResponse(final String msisdn) {
        QueryAllowedMsisdn request = new QueryAllowedMsisdn();
        request.setMsisdn(msisdn);
        request.setUsername(aatLbsUsername);
        request.setPassword(aatLbsPassword);
        return (QueryAllowedMsisdnResponse) getWebServiceTemplate().marshalSendAndReceive(request,
                message -> ((SoapMessage) message).setSoapAction("http://lbs.gsm.co.za/QueryAllowedMsisdn"));
    }

    public GetLocationResponse getLocationResponse(final String msisdn) {
        GetLocation request = new GetLocation();
        request.setMsisdn(msisdn);
        request.setUsername(aatLbsUsername);
        request.setPassword(aatLbsPassword);
        return (GetLocationResponse) getWebServiceTemplate()
                .marshalSendAndReceive(request,
                        message -> ((SoapMessage) message).setSoapAction("http://lbs.gsm.co.za/GetLocation"));
    }

}
