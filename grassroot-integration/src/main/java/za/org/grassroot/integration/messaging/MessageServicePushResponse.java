package za.org.grassroot.integration.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by luke on 2016/09/19.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageServicePushResponse {

    @JsonProperty("type")
    private MessagePushResponseType responseType;

    private boolean successful;
    private Integer errorCode;
    private String description;

    private MessageServicePushResponse() {
        // just to generate
    }

    public MessagePushResponseType getResponseType() {
        return responseType;
    }

    public void setResponseType(MessagePushResponseType responseType) {
        this.responseType = responseType;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(Integer errorCode) {
        this.errorCode = errorCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
