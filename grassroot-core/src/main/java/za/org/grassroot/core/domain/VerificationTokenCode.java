package za.org.grassroot.core.domain;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.Calendar;

/**
 * @author Lesetse Kimwaga
 */
@Entity
@Table(name = "verification_token_code")
public class VerificationTokenCode extends BaseEntity {

    private String username;

    protected String code;

    @Column(name = "creation_date")
    protected Timestamp createdDateTime;

    @Column(name = "expiry_date")
    protected Timestamp expiryDateTime;

    @Column(name = "token_access_attempts")
    private int tokenAccessAttempts = 1;

    public VerificationTokenCode() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public VerificationTokenCode(String username,String code) {
        this.code = code;
        this.username = username;
        updateTimeStamp();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Timestamp getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Timestamp createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public Timestamp getExpiryDateTime() { return expiryDateTime; }

    public void setExpiryDateTime(Timestamp expiryDateTime) { this.expiryDateTime = expiryDateTime; }

    public int getTokenAccessAttempts() {
        return tokenAccessAttempts;
    }

    public void setTokenAccessAttempts(int tokenAccessAttempts) {
        this.tokenAccessAttempts = tokenAccessAttempts;
    }

    public void updateTimeStamp() {
            this.createdDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
    }

    public  void incrementTokenAttempts()
    {
       this.tokenAccessAttempts = tokenAccessAttempts + 1;
    }

    @PreUpdate
    @PrePersist
    public void addTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
        }
        if (expiryDateTime == null) {
            expiryDateTime = new Timestamp(createdDateTime.getTime() + 5 * 60 * 1000); // default is token lasts 5 mins
        }
    }
}
