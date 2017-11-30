package za.org.grassroot.core.repository;

import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.UidIdentifiable;

import java.util.Set;

public interface UidIdentifiableRepository {
	<T extends UidIdentifiable> T findOneByUid(Class<T> returnClass, JpaEntityType entityType, String uid);

	<T extends UidIdentifiable> Set<T> findByUidIn(Class<T> returnClass, JpaEntityType entityType, Set<String> uids);

	UidIdentifiable save(UidIdentifiable entity);
}
