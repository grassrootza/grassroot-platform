package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;

/**
 * Created by paballo on 2016/03/07.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseWrapperImpl implements ResponseWrapper {

    protected String status;
    protected int code;
    protected String message;

    public ResponseWrapperImpl(){}

    public ResponseWrapperImpl(HttpStatus code, RestMessage message, RestStatus status) {
        this.code = code.value();
        this.message = String.valueOf(message);
        this.status = String.valueOf(status);
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String toString() {
        return "ResponseWrapperImpl{" +
                "status='" + status + '\'' +
                ", code=" + code +
                ", message='" + message + '\'' +
                '}';
    }
}
