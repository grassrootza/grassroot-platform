package za.org.grassroot.core.domain.movement;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import za.org.grassroot.core.domain.GrassrootEntity;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

// Just to keep track of who can take what action
// All substantive parts of movement creation/management/etc are through graph

@Entity @Slf4j
@Table(name = "movement")
public class Movement implements GrassrootEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Version
    @Column(name = "version",nullable = false)
    private Integer version;

    @Column(name = "uid", nullable = false, unique = true, updatable = false)
    @Getter private String uid;

    @Column(name = "name", nullable = false, length = 50)
    @Getter @Setter private String name;

    @Column(name = "description")
    @Getter @Setter private String description;

    @ManyToOne()
    @JoinColumn(name = "created_by_user", nullable = false, updatable = false)
    @Getter private User createdByUser;

    @Column(name = "creation_time", nullable = false, updatable = false)
    @Getter private Instant creationTime;

    @ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinTable(name = "movement_organizers",
            joinColumns = {@JoinColumn(name = "movement_id", referencedColumnName = "id", unique = false)},
            inverseJoinColumns = {@JoinColumn(name = "user_id", referencedColumnName = "id", unique = false)} )
    private Set<User> organizers = new HashSet<>();

    private Movement() {
        // for JPA
    }

    public Movement(String name, User createdByUser) {
        this.uid = UIDGenerator.generateId();
        this.creationTime = Instant.now();
        this.name = name;
        this.createdByUser = createdByUser;
        this.organizers = new HashSet<>();
        organizers.add(createdByUser);
    }

    public Set<User> getOrganizers() {
        if (organizers == null) {
            organizers = new HashSet<>();
        }
        return organizers;
    }

    public void addOrganizer(User organizer) {
        getOrganizers().add(organizer);
    }

    public void removeOrganizer(User organizer) {
        getOrganizers().remove(organizer);
    }

}
