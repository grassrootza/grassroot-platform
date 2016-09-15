package za.org.grassroot.core.domain.metamodel;

import za.org.grassroot.core.domain.AbstractTodoEntity_;
import za.org.grassroot.core.domain.TodoRequest;
import za.org.grassroot.core.domain.User;

import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(TodoRequest.class)
public abstract class TodoRequest_ extends AbstractTodoEntity_ {

	public static volatile SingularAttribute<TodoRequest, Boolean> replicateToSubgroups;
	public static volatile SetAttribute<TodoRequest, User> assignedMembers;

}

