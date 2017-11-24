package za.org.grassroot.webapp.controller.rest.exception;

import java.io.Serializable;

public class ServiceErrorMessage implements Serializable {

    private static final long serialVersionUID = 8143408458713551951L;
    private final String statusCode;
    private final String errorMessage;

    public ServiceErrorMessage(String statusCode, String errorMessage) {
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}