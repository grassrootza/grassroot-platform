package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.VerificationTokenCode;

import javax.persistence.Column;
import java.sql.Timestamp;

/**
 * Created by paballo on 2016/03/13.
 */
public class TokenDTO {
    private String code;
    private Timestamp createdDateTime;
    private Timestamp expiryDateTime;

    public TokenDTO(VerificationTokenCode tokenCode){
        this.code = tokenCode.getCode();
        this.createdDateTime = tokenCode.getCreatedDateTime();
        this.expiryDateTime =tokenCode.getExpiryDateTime();
    }

    public String getCode() {
        return code;
    }

    public Timestamp getCreatedDateTime() {
        return createdDateTime;
    }

    public Timestamp getExpiryDateTime() {
        return expiryDateTime;
    }
}
