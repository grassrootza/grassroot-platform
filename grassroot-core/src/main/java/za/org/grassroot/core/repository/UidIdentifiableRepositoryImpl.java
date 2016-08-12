package za.org.grassroot.core.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.UidIdentifiable;

import javax.persistence.EntityManager;
import java.util.Objects;

@Component
public class UidIdentifiableRepositoryImpl implements UidIdentifiableRepository {
	@Autowired
	private GroupRepository groupRepository;
	@Autowired
	private EventRepository eventRepository;
	@Autowired
	private TodoRepository todoRepository;
	@Autowired
	private EntityManager entityManager;

	@Override
	public <T extends UidIdentifiable> T findOneByUid(Class<T> returnClass, JpaEntityType entityType, String uid) {
		Objects.requireNonNull(entityType);
		Objects.requireNonNull(uid);

		UidIdentifiable uidIdentifiable = findOneByUid(entityType, uid);
		if (!returnClass.isAssignableFrom(uidIdentifiable.getClass())) {
			throw new IllegalArgumentException("Entity " + uidIdentifiable + " is not of required type " + returnClass.getCanonicalName());
		}
		return returnClass.cast(uidIdentifiable);
	}

	private UidIdentifiable findOneByUid(JpaEntityType entityType, String uid) {
		switch (entityType) {
			case GROUP:
				return groupRepository.findOneByUid(uid);
			case LOGBOOK:
				return todoRepository.findOneByUid(uid);
			case MEETING:
			case VOTE:
				return eventRepository.findOneByUid(uid);
			default:
				throw new UnsupportedOperationException("Unsupported entity type: " + entityType);
		}
	}

	@Override
	@Transactional
	public UidIdentifiable save(UidIdentifiable entity) {
		return entityManager.merge(entity);
	}
}
