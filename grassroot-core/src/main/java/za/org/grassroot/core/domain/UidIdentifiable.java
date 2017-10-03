package za.org.grassroot.core.domain;

import za.org.grassroot.core.domain.task.Task;

import java.util.Set;

public interface UidIdentifiable extends GrassrootEntity {
	JpaEntityType getJpaEntityType();

	String getUid();

	Long getId();

	String getName();

	String getDescription();

	boolean hasName();

	Set<User> getMembers();

	/**
	 * Returns group this entity belongs to, which can be this very entity if it is group itself.
	 *
	 * @return group itself, or group this entity belongs to
	 */
	default Group getThisOrAncestorGroup() {
		if (this instanceof Group) {
			return (Group) this;
		} else if (this instanceof Task) {
			return ((Task) this).getAncestorGroup();
		} else {
			throw new UnsupportedOperationException("Cannot resolve group if this entity is not " + Task.class.getCanonicalName() + " or Group itself, but is: " + this);
		}
	}
}