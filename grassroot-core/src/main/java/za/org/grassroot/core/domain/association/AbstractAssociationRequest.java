package za.org.grassroot.core.domain.association;

import org.slf4j.Logger;
import za.org.grassroot.core.domain.GrassrootEntity;
import za.org.grassroot.core.enums.AssocRequestStatus;
import za.org.grassroot.core.enums.AssociationRequestType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;

/**
 * Created by luke on 2017/02/06.
 *
 * NB design note: Have decided to go for a normalized design here (so each type of join request gets its own table),
 * because (a) this is not going to be read intensive (often 1 write to create request, a coupl reads at most to
 * view details & approve or not), (b) each such entity will join on either side, but that's it, i.e., not much
 * need to have many joins, overcoming key disadvantage of normalizing, and (c) having one big join_request table
 * would require additional enums & columns to keep track of entity types, then indexes on those discriminator columns.
 * Note: can always revisit this later, depending on voume.
 *
 */
@MappedSuperclass
public abstract class AbstractAssociationRequest<R extends GrassrootEntity, D extends GrassrootEntity> {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(AbstractAssociationRequest.class);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    protected Long id;

    @Column(name = "uid", length = 50, nullable = false)
    protected String uid;

    @Column(name = "creation_time", insertable = true, updatable = false, nullable = false)
    protected Instant creationTime;

    @Column(name = "description")
    protected String description;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    protected AssocRequestStatus status;

    @Column(name = "processed_time")
    protected Instant processedTime;

    protected AbstractAssociationRequest() {
        // for JPA
    }

    protected AbstractAssociationRequest(String description) {
        logger.info("Inside abstract entity constructor");
        this.uid = UIDGenerator.generateId();
        this.creationTime = Instant.now();
        this.status = AssocRequestStatus.PENDING;
        this.description = description;
    }

    /*
	Version used by hibernate to resolve conflicting updates. Do not update set it, it is for Hibernate only
     */
    @Version
    protected Integer version;

    public Long getId() {
        return id;
    }

    public String getUid() {
        return uid;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public Instant getProcessedTime() { return processedTime; }

    public AssocRequestStatus getStatus() {
        return status;
    }

    public void setProcessedTime(Instant processedTime) {
        this.processedTime = processedTime;
    }

    public void setStatus(AssocRequestStatus status) {
        this.status = status;
    }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public abstract R getRequestor();

    public abstract D getDestination();

    public abstract AssociationRequestType getType();

}
