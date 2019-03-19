package za.org.grassroot.core.domain.group;

import lombok.AccessLevel;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.TagHolder;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.GroupViewPriority;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Table(name = "group_user_membership",
        uniqueConstraints = @UniqueConstraint(name = "uk_membership_group_user", columnNames = {"group_id", "user_id"}))
public class Membership implements Serializable, TagHolder {

    private static final String JOIN_METHOD_DESCRIPTOR_TAG = "JOINDESC:";
    private static final String AFFILITATION_TAG ="AFFILIATION:";

    @Setter(AccessLevel.PRIVATE)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

//    @ManyToOne(optional = false)
    @ManyToOne(optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "role", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private GroupRole role;

    @Column(name = "join_time", nullable = false)
    private Instant joinTime;

    @Column(name = "user_join_method")
    @Enumerated(EnumType.STRING)
    private GroupJoinMethod joinMethod;

    @Setter
    @Basic
    @Column(name = "alias", length = 50, nullable = true)
    private String alias;

    @Column(name = "tags")
    @Type(type = "za.org.grassroot.core.util.StringArrayUserType")
    private String[] tags;

    @Setter
    @Column(name = "view_priority", nullable = false)
    @Enumerated(EnumType.STRING)
    private GroupViewPriority viewPriority;

    private Membership() {
        // for JPA
    }

    public Membership(Group group, User user, GroupRole role, Instant joinTime, GroupJoinMethod joinMethod, String joinMethodDescriptor) {
        this.group = Objects.requireNonNull(group);
        this.user = Objects.requireNonNull(user);
        this.role = Objects.requireNonNull(role);
        this.joinTime = Objects.requireNonNull(joinTime);
        this.joinMethod = joinMethod;
        this.viewPriority = GroupViewPriority.NORMAL; // no case where starts off pinned or hidden

        if (!StringUtils.isEmpty(joinMethodDescriptor)) {
            this.addTag(JOIN_METHOD_DESCRIPTOR_TAG + joinMethodDescriptor);
        }
    }

    /**
     * Just a convenience
     * @return
     */
    public Set<Permission> getRolePermissions() {
        return this.group.getPermissions(this.role);
    }

    public Optional<String> getJoinMethodDescriptor() {
        return this.getTagList().stream().filter(s -> s.startsWith(JOIN_METHOD_DESCRIPTOR_TAG))
                .map(s -> s.substring(JOIN_METHOD_DESCRIPTOR_TAG.length())).findFirst();
    }

    public void addAffiliations(Set<String> affiliations) {
        if (affiliations != null && !affiliations.isEmpty()) {
            this.addTags(affiliations.stream().map(s -> AFFILITATION_TAG + s).collect(Collectors.toList()));
        }
    }

    public void setAffiliations(Set<String> affiliations) {
        // first get all the non-affiliation tags
        List<String> tags = getTagList().stream()
                .filter(s -> !s.startsWith(AFFILITATION_TAG)).collect(Collectors.toList());
        // then add the topics
        tags.addAll(affiliations.stream().map(s -> AFFILITATION_TAG + s).collect(Collectors.toSet()));
        setTags(tags);
    }

    public List<String> getAffiliations() {
		Collection<String> tagList = this.getTagList();
		return extractAffiliations(tagList).collect(Collectors.toList());
    }

	public static Stream<String> extractAffiliations(Collection<String> tagList) {
		return tagList.stream()
                .filter(s -> s.startsWith(AFFILITATION_TAG))
                .map(s -> s.substring(AFFILITATION_TAG.length()));
	}

	public void updateRole(GroupRole role) {
        this.role = Objects.requireNonNull(role);
    }

    public String getDisplayName() {
        return alias == null || alias.trim().isEmpty() ? user.getName() : alias;
    }

    public boolean hasAnyTopic(Collection<String> topics) {
        return getTopics().stream().anyMatch(topics::contains);
    }

	public Group getGroup() {
		return group;
	}

	public User getUser() {
		return user;
	}

    public GroupRole getRole() {
        return role;
    }

    public Instant getJoinTime() {
		return joinTime;
	}

	public GroupJoinMethod getJoinMethod() {
		return joinMethod;
	}

	public String getAlias() {
		return alias;
	}

	public GroupViewPriority getViewPriority() {
		return viewPriority;
	}

	public Long getId() {
		return id;
	}

	@Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof Membership)) {
            return false;
        }

        Membership that = (Membership) o;

        if (getGroup() != null ? !getGroup().equals(that.getGroup()) : that.getGroup() != null) {
            return false;
        }

        return getUser() != null ? getUser().equals(that.getUser()) : that.getUser() == null;

    }

    @Override
    public int hashCode() {
        int result = getGroup() != null ? getGroup().hashCode() : 0;
        result = 31 * result + (getUser() != null ? getUser().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Membership{");
        sb.append("group=").append(group);
        sb.append(", user=").append(user);
        sb.append(", role=").append(role);
        sb.append('}');
        return sb.toString();
    }

    public String[] getTags() {
        return tags == null ? new String[0] : tags;
    }

    @Override
    public void setTags(String[] tags) {
        this.tags = tags;
    }
}
