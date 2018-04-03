package za.org.grassroot.core.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import za.org.grassroot.core.enums.VerificationCodeType;

import javax.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * @author Lesetse Kimwaga
 */
@Entity @Slf4j
@Table(name = "verification_token_code",
        indexes = {@Index(name = "idx_code_username", columnList = "username, code", unique = false),
        @Index(name = "idx_entity_user_uid", columnList = "entity_uid, user_uid", unique = true)})
public class VerificationTokenCode {

    private static final int MAX_TOKEN_ACCESS_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column( name = "id")
    protected Long id;

    @Column(name = "username", nullable = false)
    @Getter private String username;

    // above was needed in some legacy code, transition to this over time, as more robust
    @Column(name = "user_uid")
    @Getter private String userUid;

    @Column(name = "code", nullable = false)
    @Getter protected String code;

    @Column(name = "creation_date", nullable = false)
    @Getter private Instant createdDateTime;

    @Column(name = "expiry_date")
    @Getter @Setter private Instant expiryDateTime;

    @Column(name = "token_access_attempts")
    private int tokenAccessAttempts = 0;

    @Column(name = "token_type", nullable = false)
    @Enumerated(EnumType.STRING)
    @Getter private VerificationCodeType type;

    // used for responses
    @Column(name = "entity_uid")
    @Getter @Setter private String entityUid;

    private VerificationTokenCode() {
        // for JPA
    }

    public VerificationTokenCode(String username, String code, VerificationCodeType type, String userUid) {
        Objects.requireNonNull(username);
        Objects.requireNonNull(code);

        this.code = code;
        this.username = username;
        this.userUid = userUid;
        this.type = type;
        this.createdDateTime = Instant.now();
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

    public void setCode(String code) {
        Objects.requireNonNull(code);
        this.code = code;
    }

    public void incrementTokenAttempts() {
       this.tokenAccessAttempts = this.tokenAccessAttempts + 1;
       if (this.tokenAccessAttempts > MAX_TOKEN_ACCESS_ATTEMPTS) {
           this.setExpiryDateTime(Instant.now());
       }
       log.info("token code {}, access attempts now = {}", this.code, this.tokenAccessAttempts);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VerificationTokenCode that = (VerificationTokenCode) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(username, that.username) &&
                Objects.equals(userUid, that.userUid) &&
                Objects.equals(code, that.code) &&
                Objects.equals(createdDateTime, that.createdDateTime) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username, userUid, code, createdDateTime, type);
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