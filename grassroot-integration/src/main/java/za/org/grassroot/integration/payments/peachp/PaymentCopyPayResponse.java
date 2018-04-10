package za.org.grassroot.integration.payments.peachp;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.regex.Pattern;

@Getter @Setter @NoArgsConstructor @ToString
public class PaymentCopyPayResponse {

    public static final Pattern SUCCESS_MATCHER = Pattern.compile("^(000\\.000\\.|000\\.100\\.1|000\\.[36])");

    private String code;
    private Result result;
    private String buildNumber;
    // private ZonedDateTime timestamp;
    private String ndc;
    private String id;
    private String registrationId;

    @NoArgsConstructor @Getter @Setter
    private class Result {
        private String code;
        private String description;

        @Override
        public String toString() {
            return "Result{" +
                    "code='" + code + '\'' +
                    ", description='" + description + '\'' +
                    '}';
        }
    }

    public String getInternalCode() {
        return result.code;
    }

}
