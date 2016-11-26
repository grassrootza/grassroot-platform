package za.org.grassroot.integration.payments;

/**
 * Created by luke on 2016/11/24.
 */
public enum PaymentResultType {

    // success types
    SUCCESS,
    REVIEW,
    PENDING,

    NOT_IN_3D,

    FAILED_3D,
    FAILED_BANK,
    FAILED_COMMS,
    FAILED_SYSTEM,
    FAILED_ADDRESS,
    FAILED_FORMAT,
    FAILED_WORKFLOW,
    FAILED_RISK,

    FAILED_OTHER

}
