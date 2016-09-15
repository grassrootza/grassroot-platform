package za.org.grassroot.core.domain.metamodel;

import java.time.Instant;
import javax.annotation.Generated;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.GroupDefaultImage;

@StaticMetamodel(Group.class)
public abstract class Group_ {

	public static volatile SingularAttribute<Group, Group> parent;
	public static volatile SingularAttribute<Group, Instant> tokenExpiryDateTime;
	public static volatile SingularAttribute<Group, User> createdByUser;
	public static volatile SingularAttribute<Group, Integer> reminderMinutes;
	public static volatile SingularAttribute<Group, Instant> createdDateTime;
	public static volatile SetAttribute<Group, Role> groupRoles;
	public static volatile SingularAttribute<Group, String> description;
	public static volatile SetAttribute<Group, Membership> memberships;
	public static volatile SingularAttribute<Group, String> uid;
	public static volatile SingularAttribute<Group, String> defaultLanguage;
	public static volatile SetAttribute<Group, Group> children;
	public static volatile SingularAttribute<Group, String> imageUrl;
	public static volatile SingularAttribute<Group, Long> id;
	public static volatile SingularAttribute<Group, String> groupTokenCode;
	public static volatile SetAttribute<Group, Event> events;
	public static volatile SingularAttribute<Group, GroupDefaultImage> defaultImage;
	public static volatile SingularAttribute<Group, byte[]> image;
	public static volatile SetAttribute<Group, Event> descendantEvents;
	public static volatile SingularAttribute<Group, Boolean> active;
	public static volatile SingularAttribute<Group, Boolean> paidFor;
	public static volatile SingularAttribute<Group, Integer> version;
	public static volatile SingularAttribute<Group, User> joinApprover;
	public static volatile SingularAttribute<Group, String> groupName;
	public static volatile SingularAttribute<Group, Boolean> discoverable;
	public static volatile SetAttribute<Group, Todo> todos;
	public static volatile SetAttribute<Group, Todo> descendantTodos;

}

