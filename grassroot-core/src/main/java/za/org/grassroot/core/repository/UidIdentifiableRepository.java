package za.org.grassroot.core.repository;

import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.UidIdentifiable;

public interface UidIdentifiableRepository {
	<T extends UidIdentifiable> T findOneByUid(Class<T> returnClass, JpaEntityType entityType, String uid);

	UidIdentifiable save(UidIdentifiable entity);
}
