package za.org.grassroot.integration.payments;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by luke on 2016/11/01.
 */
public class PaymentResultPP {

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

    public PaymentParameterError[] getParameterErrors() {
        return parameterErrors;
    }

    public void setParameterErrors(PaymentParameterError[] parameterErrors) {
        this.parameterErrors = parameterErrors;
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
