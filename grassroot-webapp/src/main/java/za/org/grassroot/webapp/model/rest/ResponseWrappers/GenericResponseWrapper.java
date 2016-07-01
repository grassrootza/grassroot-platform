package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import org.springframework.http.HttpStatus;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;

/**
 * Created by paballo on 2016/03/12.
 */
public class GenericResponseWrapper extends ResponseWrapperImpl {
   private final Object data;

    public GenericResponseWrapper(HttpStatus code, RestMessage message, RestStatus status, Object data) {
        super(code,message,status);
        this.data =data;
    }

    public Object getData() {
        return data;
    }

    @Override
    public String toString() {
        return "GenericResponseWrapper{" +
                "data=" + data +
                "status='" + status + '\'' +
                ", code=" + code +
                ", message='" + message + '\'' +
                '}';
    }
}
