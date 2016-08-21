package za.org.grassroot.core.domain;

import java.util.Set;

public interface TodoContainer extends UidIdentifiable {
	Set<Todo> getTodos();
}
