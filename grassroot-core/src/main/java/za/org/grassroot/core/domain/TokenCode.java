package za.org.grassroot.core.domain;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.Calendar;

/**
 * Created by luke on 2015/08/30.
 */

/* @MappedSuperclass
public abstract class TokenCode extends BaseEntity {

    protected String code;
    protected Timestamp createdDateTime;
    protected Timestamp expiryDateTime;

    public TokenCode() {
    }

    public TokenCode withCode(String code) {
        this.code = code;
        return this;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Column(name = "creation_date")
    public Timestamp getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Timestamp createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    @Column(name = "expiry_date")
    public Timestamp getExpiryDateTime() { return expiryDateTime; }

    public void setExpiryDateTime(Timestamp expiryDateTime) { this.expiryDateTime = expiryDateTime; }

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

} */