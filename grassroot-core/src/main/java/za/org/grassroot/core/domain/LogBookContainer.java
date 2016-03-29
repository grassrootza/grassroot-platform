package za.org.grassroot.core.domain;

import java.util.Set;

public interface LogBookContainer extends UidIdentifiable {
	Set<LogBook> getLogBooks();
}
