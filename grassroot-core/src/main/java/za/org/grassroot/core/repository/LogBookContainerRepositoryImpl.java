package za.org.grassroot.core.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.LogBookContainer;

import java.util.Set;

@Component
public class LogBookContainerRepositoryImpl implements LogBookContainerRepository {
	@Autowired
	private UidIdentifiableRepository uidIdentifiableRepository;

	@Override
	@Transactional(readOnly = true)
	public Set<LogBook> findAll(JpaEntityType logBookContainerType, String logBookContainerUid) {
		LogBookContainer logBookContainer = uidIdentifiableRepository.findOneByUid(LogBookContainer.class, logBookContainerType, logBookContainerUid);
		return logBookContainer.getLogBooks();
	}
}
