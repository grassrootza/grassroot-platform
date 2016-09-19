package za.org.grassroot.integration.sms.aat;

import za.org.grassroot.integration.sms.SmsGatewayResponse;
import za.org.grassroot.integration.sms.SmsResponseType;

/**
 * Created by luke on 2016/09/19.
 * utility class to turn the JAXB-interpreted AAT response into something more abstract and usable
 */
public class AatResponseInterpreter implements SmsGatewayResponse {

    private static final String successAction = "enqueued";

    private SmsResponseType responseType;
    private boolean successful;
    private Integer aatErrorCode;

    public AatResponseInterpreter(AatSmsResponse rawResponse) {
        if (rawResponse.getSubmitresult().getAction().equals(successAction)) {
            this.responseType = SmsResponseType.ROUTED;
            this.successful = true;
        } else if (rawResponse.getSubmitresult().error != null) {
            this.successful = false;
            this.aatErrorCode = rawResponse.getSubmitresult().error;
            switch (aatErrorCode) {
                case 150:
                case 151:
                case 152:
                    this.responseType = SmsResponseType.INVALID_CREDENTIALS;
                    break;
                case 153:
                    this.responseType = SmsResponseType.INSUFFICIENT_FUNDS;
                    break;
                case 154:
                case 156:
                    this.responseType = SmsResponseType.MSISDN_INVALID;
                    break;
                case 155:
                    this.responseType = SmsResponseType.DUPLICATE_MESSAGE;
                    break;
                default:
                    this.responseType = SmsResponseType.UNKNOWN_ERROR;
                    break;
            }
        } else {
            this.successful = false;
            this.responseType = SmsResponseType.UNKNOWN_ERROR;
        }
    }

    @Override
    public SmsResponseType getResponseType() {
        return responseType;
    }

    @Override
    public boolean isSuccessful() {
        return successful;
    }

    @Override
    public Integer getErrorCode() {
        return aatErrorCode;
    }

    public Integer getAatErrorCode() {
        return aatErrorCode;
    }

    @Override
    public String toString() {
        return "AatResponseInterpreter{" +
                "responseType=" + responseType +
                ", successful=" + successful +
                ", errorCode=" + getErrorCode() +
                '}';
    }
}
