package za.org.grassroot.core.domain;

public interface UidIdentifiable {
	JpaEntityType getJpaEntityType();

	String getUid();

	Long getId();

	String getName();

	/**
	 * Returns group this entity belongs to, which can be this very entity if it is group itself.
	 *
	 * @return group itself, or group this entity belongs to
	 */
	default Group getThisOrAncestorGroup() {
		if (this instanceof Group) {
			return (Group) this;
		} else if (this instanceof GroupDescendant) {
			return ((GroupDescendant) this).getAncestorGroup();
		} else {
			throw new UnsupportedOperationException("Cannot resolve group if this entity is not " + GroupDescendant.class.getCanonicalName() + " or Group itsel, but is: " + this);
		}
	}
}