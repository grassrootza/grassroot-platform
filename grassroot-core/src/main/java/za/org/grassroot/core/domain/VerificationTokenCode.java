package za.org.grassroot.core.domain;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.Calendar;

/**
 * @author Lesetse Kimwaga
 */
@Entity
@Table(name = "verification_token_code")
public class VerificationTokenCode extends TokenCode {

    private String username;
    private int tokenAccessAttempts = 1;

    public VerificationTokenCode() {
    }

    public VerificationTokenCode(String username,String code) {
        this.code = code;
        this.username = username;
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

    public void incrementTokenAttempts()
    {
       this.tokenAccessAttempts = tokenAccessAttempts + 1;
    }
}
