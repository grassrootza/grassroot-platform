package za.org.grassroot.core.domain;

public interface UidIdentifiable {
	JpaEntityType getJpaEntityType();
	String getUid();
	Long getId();

	/**
	 * Returns group this entity belongs to, which can be this very entity if it is group itself.
	 * @return group itself, or group this entity belongs to
	 */
	default Group resolveGroup() {
		UidIdentifiable currentEntity = this;
		while (!currentEntity.getJpaEntityType().equals(JpaEntityType.GROUP)) {
			switch (currentEntity.getJpaEntityType()) {
				case LOGBOOK:
					currentEntity = ((LogBook) currentEntity).getParent();
					break;
				case MEETING:
					currentEntity = ((Meeting) currentEntity).getParent();
					break;
				case VOTE:
					currentEntity = ((Meeting) currentEntity).getParent();
					break;
				default:
					throw new UnsupportedOperationException("Invalid " + JpaEntityType.class.getSimpleName() +
							" " + currentEntity.getJpaEntityType() + "; " + currentEntity);
			}
		}
		return (Group) currentEntity;
	};
}
