package za.org.grassroot.core.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoContainer;

import java.util.Set;

@Component
public class TodoContainerRepositoryImpl implements TodoContainerRepository {
	@Autowired
	private UidIdentifiableRepository uidIdentifiableRepository;

	@Override
	@Transactional(readOnly = true)
	public Set<Todo> findAll(JpaEntityType logBookContainerType, String logBookContainerUid) {
		TodoContainer todoContainer = uidIdentifiableRepository.findOneByUid(TodoContainer.class, logBookContainerType, logBookContainerUid);
		return todoContainer.getTodos();
	}
}
