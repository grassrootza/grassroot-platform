package za.org.grassroot.webapp.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @NoArgsConstructor @ToString
public class PaymentSystemResponse {

    private String code;

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

    private Result result;
    private String buildNumber;
    // private ZonedDateTime timestamp;
    private String ndc;
    private String id;

    public String getInternalCode() {
        return result.code;
    }

}
