package za.org.grassroot.webapp.model.rest.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.webapp.enums.RestStatus;

/**
 * Created by paballo on 2016/03/07.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PermissionLackingWrapper implements ResponseWrapper {

    protected final String status;
    protected final int code;
    protected final String message;

    public PermissionLackingWrapper(HttpStatus code, Permission permission, RestStatus status) {
        this.code = code.value();
        this.message = permission.getName();
        this.status = String.valueOf(status);
    }

    @Override
    public String toString() {
        return "PermissionLacking{" +
                "status='" + status + '\'' +
                ", code=" + code +
                ", message='" + message + '\'' +
                '}';
    }
}
