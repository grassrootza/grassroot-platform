package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.VerificationCodeType;

import javax.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * @author Lesetse Kimwaga
 */
@Entity
@Table(name = "verification_token_code")
public class VerificationTokenCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column( name = "id")
    protected Long id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "code", nullable = false)
    protected String code;

    @Column(name = "creation_date", nullable = false)
    private Instant createdDateTime;

    @Column(name = "expiry_date")
    private Instant expiryDateTime;

    @Column(name = "token_access_attempts")
    private int tokenAccessAttempts = 1;

    @Column(name = "token_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private VerificationCodeType type;



    private VerificationTokenCode() {
        // for JPA
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        Objects.requireNonNull(code);
        this.code = code;
    }

    public VerificationTokenCode(String username, String code, VerificationCodeType type) {
        Objects.requireNonNull(username);
        Objects.requireNonNull(code);

        this.code = code;
        this.username = username;
        this.type = type;
        updateCreatedDateTime();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Instant createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public Instant getExpiryDateTime() { return expiryDateTime; }

    public void setExpiryDateTime(Instant expiryDateTime) { this.expiryDateTime = expiryDateTime; }

    public int getTokenAccessAttempts() {
        return tokenAccessAttempts;
    }

    public void setTokenAccessAttempts(int tokenAccessAttempts) {
        this.tokenAccessAttempts = tokenAccessAttempts;
    }

    public void updateCreatedDateTime() {
        this.createdDateTime = Instant.now();
    }

    public void incrementTokenAttempts()
    {
       this.tokenAccessAttempts = tokenAccessAttempts + 1;
    }

    public VerificationCodeType getType() {
        return type;
    }

    public void setType(VerificationCodeType type) {
        this.type = type;
    }

    @PreUpdate
    @PrePersist
    public void addTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = Instant.now();
        }
        if (expiryDateTime == null) {
            expiryDateTime = createdDateTime.plus(5, ChronoUnit.MINUTES); // default is token lasts 5 mins
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        VerificationTokenCode that = (VerificationTokenCode) o;

        return id.equals(that.id);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + id.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "VerificationTokenCode{" +
                "username='" + username + '\'' +
                ", expiryDateTime=" + expiryDateTime +
                ", code='" + code + '\'' +
                '}';
    }
}