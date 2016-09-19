package za.org.grassroot.integration.sms;

/**
 * Created by luke on 2016/09/19.
 */
public enum SmsResponseType {

    ROUTED,
    DELIVERED,
    INVALID_CREDENTIALS,
    INSUFFICIENT_FUNDS,
    DUPLICATE_MESSAGE,
    MSISDN_INVALID,
    INVALID_QUERY,
    UNKNOWN_ERROR

}
