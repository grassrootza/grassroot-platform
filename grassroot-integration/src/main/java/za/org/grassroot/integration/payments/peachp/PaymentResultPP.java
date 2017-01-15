package za.org.grassroot.integration.payments.peachp;

import za.org.grassroot.integration.payments.PaymentResultType;

import java.util.Arrays;
import java.util.regex.Pattern;

import static za.org.grassroot.integration.payments.PaymentResultType.*;

/**
 * Created by luke on 2016/11/01.
 */
public class PaymentResultPP {

    // codes corresponding to result
    private static final Pattern successPattern = Pattern.compile("000\\.[0136]00\\.[01]\\d{2}");
    private static final Pattern okayButReview = Pattern.compile("000\\.400\\.(0\\d{2}|100)");
    private static final Pattern pendingTrans = Pattern.compile("000\\.200\\.[01]\\d{2}");

    private static final Pattern notIn3D = Pattern.compile("100.390.10[79]");

    private static final Pattern rejected3d = Pattern.compile("000\\.400\\.(1[0-9][1-9]|2\\d{2})");
    private static final Pattern rejectedBank = Pattern.compile("800\\.[178]00\\.[1245][05679]\\d");
    private static final Pattern rejectedComms = Pattern.compile("900\\.[1-4]00\\.[1-6]\\d{2}");
    private static final Pattern rejectedSystem = Pattern.compile("(600|800|999)\\.(100|500|800|999)\\.[189][0189][0189]");

    private static final Pattern asyncWorkflowError = Pattern.compile("100\\.39[567]\\.[125]\\d{2}");
    private static final Pattern riskHandlingError = Pattern.compile("(100\\.400\\.\\d{3}|100\\.380\\.\\d{3}|100\\.370\\.100|100\\.370\\.11\\d])");
    private static final Pattern addressError = Pattern.compile("800\\.400\\.1\\d{2}");

    private static final Pattern rejected3dOther = Pattern.compile("(800\\.400\\.2\\d{2})|(100\\.380\\.4\\d{2})|(100\\.390\\.\\d{3})");
    private static final Pattern blacklistRejection = Pattern.compile("(100\\.100\\.701|800\\.[32]00\\.[1-5]\\d{2})");

    private static final Pattern riskValidationError = Pattern.compile("800\\.1[123456]0\\.\\d{3}");

    private static final Pattern configValidationError = Pattern.compile("(600\\.2[02][01]\\.\\d{3}|500\\.[12]\\d{2}\\.\\d{3}|800\\.121\\.\\d{3})");
    private static final Pattern regValidationError = Pattern.compile("100\\.[13]50\\.\\d{3}");
    private static final Pattern jobValidationError = Pattern.compile("100\\.(250|360)\\.\\d{3}");
    private static final Pattern referenceValidError = Pattern.compile("700\\.[1345][05][0]\\.\\d{3}");

    private static final Pattern formatError = Pattern.compile("((200\\.[123]00|100\\.[53][07]\\d|800\\.900)\\.\\d{3}|100\\.[69]00\\.500)");
    private static final Pattern addressError2 = Pattern.compile("100\\.800\\.[1-5]\\d{2}");

    private static final Pattern contactValidError = Pattern.compile("100\\.[97]00\\.\\d{3}");
    private static final Pattern accountValidError = Pattern.compile("(100\\.100\\.\\d{3}|100\\.2[01]\\d\\.\\d{3})");

    private static final Pattern amountError = Pattern.compile("100\\.55\\d\\.\\d{3}");
    private static final Pattern riskMgmtError = Pattern.compile("(100\\.380\\.[23]\\d{2}|100\\.380\\.101)");

    private static final Pattern chargebackCodes = Pattern.compile("000\\.100\\.2\\d{2}");

    private String code;
    private String description;
    private PaymentParameterError[] parameterErrors;

    public PaymentResultPP() {
        // for Object Mapper
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String fullDescription() { return code + ": " + description; }

    public PaymentParameterError[] getParameterErrors() {
        return parameterErrors;
    }

    public void setParameterErrors(PaymentParameterError[] parameterErrors) {
        this.parameterErrors = parameterErrors;
    }

    public boolean isSuccessful() {
        return successPattern.matcher(code).matches();
    }

    public PaymentResultType getType() {
        return successPattern.matcher(code).matches() ? SUCCESS :
                notIn3D.matcher(code).matches() ? NOT_IN_3D :
               pendingTrans.matcher(code).matches() ? PENDING :
               okayButReview.matcher(code).matches() ? REVIEW :
               (rejected3d.matcher(code).matches() || rejected3dOther.matcher(code).matches()) ? FAILED_3D :
               rejectedBank.matcher(code).matches() ? FAILED_BANK :
               rejectedComms.matcher(code).matches() ? FAILED_COMMS :
               rejectedSystem.matcher(code).matches() ? FAILED_SYSTEM :
               asyncWorkflowError.matcher(code).matches() ? FAILED_WORKFLOW :
               (blacklistRejection.matcher(code).matches() || riskHandlingError.matcher(code).matches() ||
                       riskValidationError.matcher(code).matches()) ? FAILED_RISK :
               (addressError.matcher(code).matches() || addressError2.matcher(code).matches()) ? FAILED_ADDRESS :
               (configValidationError.matcher(code).matches() || regValidationError.matcher(code).matches() ||
                       referenceValidError.matcher(code).matches() || contactValidError.matcher(code).matches() ||
                               formatError.matcher(code).matches()) ? FAILED_FORMAT : FAILED_OTHER;

    }

    @Override
    public String toString() {
        return "PaymentResultPP{" +
                "code='" + code + '\'' +
                ", description='" + description + '\'' +
                ", parameterErrors=" + Arrays.toString(parameterErrors) +
                '}';
    }
}
