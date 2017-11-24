package za.org.grassroot.core.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.UidIdentifiable;

import javax.persistence.EntityManager;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
			case TODO:
				return todoRepository.findOneByUid(uid);
			case MEETING:
			case VOTE:
				return eventRepository.findOneByUid(uid);
			default:
				throw new UnsupportedOperationException("Unsupported entity type: " + entityType);
		}
	}

	@Override
	public <T extends UidIdentifiable> Set<T> findByUidIn(Class<T> returnClass, JpaEntityType entityType, Set<String> uids) {
		Objects.requireNonNull(entityType);
		Objects.requireNonNull(uids);

		Set<UidIdentifiable> uidIdentifiables = findByUidsIn(entityType, uids);

		if (uidIdentifiables == null || uidIdentifiables.isEmpty()) {
			return Collections.emptySet();
		} else {
			if (!returnClass.isAssignableFrom(uidIdentifiables.iterator().next().getClass())) {
				throw new IllegalArgumentException("Entity " + uidIdentifiables.iterator().next() + " is not of required type " + returnClass.getCanonicalName());
			}
			return uidIdentifiables.stream().map(returnClass::cast).collect(Collectors.toSet());
		}
	}

	private Set<UidIdentifiable> findByUidsIn(JpaEntityType entityType, Set<String> uids) {
		switch (entityType) {
			case GROUP:
				return groupRepository.findByUidIn(uids).stream().map(g -> (UidIdentifiable) g).collect(Collectors.toSet());
			case TODO:
				return todoRepository.findByUidIn(uids).stream().map(t -> (UidIdentifiable) t).collect(Collectors.toSet());
			case MEETING:
			case VOTE:
				return eventRepository.findByUidIn(uids).stream().map(e -> (UidIdentifiable) e).collect(Collectors.toSet());
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
