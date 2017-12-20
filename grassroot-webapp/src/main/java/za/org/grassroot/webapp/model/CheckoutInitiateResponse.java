package za.org.grassroot.webapp.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class CheckoutInitiateResponse {

    private String code;

    @NoArgsConstructor @Getter @Setter
    private class Result {
        private String code;
        private String description;
    }

    private Result result;
    private String buildNumber;
    // private ZonedDateTime timestamp;
    private String ndc;
    private String id;

}
