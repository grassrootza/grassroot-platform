package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.VerificationTokenCode;

import java.time.Instant;

/**
 * Created by paballo on 2016/03/13.
 */
public class TokenDTO {
    private String code;
    private long createdDateTime;
    private long expiryDateTime;

    public TokenDTO(VerificationTokenCode tokenCode){
        this.code = tokenCode.getCode();
        this.createdDateTime = tokenCode.getCreatedDateTime().toEpochMilli();
        this.expiryDateTime = tokenCode.getExpiryDateTime().toEpochMilli();
    }

    public String getCode() {
        return code;
    }

    public long getCreatedDateTime() {
        return createdDateTime;
    }

    public long getExpiryDateTime() {
        return expiryDateTime;
    }
}
