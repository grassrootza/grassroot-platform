package za.org.grassroot.core.domain.geo;

import za.org.grassroot.core.domain.Group;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "group_location",
		uniqueConstraints = @UniqueConstraint(name = "uk_group_location_group_date", columnNames = {"group_id", "local_date"}))
public class GroupLocation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "group_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_group_location_group"))
	private Group group;

	@Column(name = "local_date", nullable = false)
	private LocalDate localDate;

	private GeoLocation location;

	@Column(name = "score", nullable = false)
	private float score;

	private GroupLocation() {
		// for JPA
	}

	public GroupLocation(Group group, LocalDate localDate, GeoLocation location, float score) {
		this.group = Objects.requireNonNull(group);
		this.localDate = Objects.requireNonNull(localDate);
		this.location = Objects.requireNonNull(location);

		if (score < 0 || score > 1) {
			throw new IllegalArgumentException("Score has to be between 0 and 1, but is: " + score);
		}
		this.score = score;
	}

	public Long getId() {
		return id;
	}

	public Group getGroup() {
		return group;
	}

	public LocalDate getLocalDate() {
		return localDate;
	}

	public GeoLocation getLocation() {
		return location;
	}

	public float getScore() {
		return score;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		GroupLocation that = (GroupLocation) o;

		if (group != null ? !group.equals(that.group) : that.group != null) {
			return false;
		}
		return localDate != null ? localDate.equals(that.localDate) : that.localDate == null;

	}

	@Override
	public int hashCode() {
		int result = group != null ? group.hashCode() : 0;
		result = 31 * result + (localDate != null ? localDate.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("GroupLocation{");
		sb.append("id=").append(id);
		sb.append(", group=").append(group);
		sb.append(", localDate=").append(localDate);
		sb.append(", location=").append(location);
		sb.append(", score=").append(score);
		sb.append('}');
		return sb.toString();
	}
}
