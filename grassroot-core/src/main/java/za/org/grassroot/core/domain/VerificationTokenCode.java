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

    private String code;
    private java.sql.Timestamp createdDateTime;
    private String username;
    private int tokenAccessAttempts = 1;

    public VerificationTokenCode() {
    }

    public VerificationTokenCode(String username,String code) {
        this.code = code;
        this.username = username;
        updateTimeStamp();
    }

    public VerificationTokenCode withCode(String code) {
        this.code = code;
        updateTimeStamp();
        return this;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
        updateTimeStamp();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Column(name = "token_access_attempts")
    public int getTokenAccessAttempts() {
        return tokenAccessAttempts;
    }

    public void setTokenAccessAttempts(int tokenAccessAttempts) {
        this.tokenAccessAttempts = tokenAccessAttempts;
    }

    @Column(name = "creation_date")
    public Timestamp getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Timestamp createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    @PreUpdate
    @PrePersist
    public void addTimeStamp() {
        if (createdDateTime == null) {
            createdDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
        }
    }

    public void updateTimeStamp() {
            this.createdDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
    }



    public  void incrementTokenAttempts()
    {
       this.tokenAccessAttempts = tokenAccessAttempts + 1;
    }
}
