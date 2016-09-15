package za.org.grassroot.core.domain.metamodel;

import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.UserMessagingPreference;

import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import java.time.Instant;

@StaticMetamodel(User.class)
public abstract class User_ {

	public static volatile SingularAttribute<User, String> lastName;
	public static volatile SetAttribute<User, Role> standardRoles;
	public static volatile SingularAttribute<User, Boolean> hasAndroidProfile;
	public static volatile SingularAttribute<User, String> displayName;
	public static volatile SingularAttribute<User, AlertPreference> alertPreference;
	public static volatile SingularAttribute<User, Integer> notificationPriority;
	public static volatile SingularAttribute<User, Boolean> hasInitiatedSession;
	public static volatile SingularAttribute<User, Instant> createdDateTime;
	public static volatile SingularAttribute<User, String> languageCode;
	public static volatile SingularAttribute<User, UserMessagingPreference> messagingPreference;
	public static volatile SingularAttribute<User, Integer> version;
	public static volatile SingularAttribute<User, Boolean> enabled;
	public static volatile SetAttribute<User, Membership> memberships;
	public static volatile SingularAttribute<User, String> uid;
	public static volatile SingularAttribute<User, String> firstName;
	public static volatile SingularAttribute<User, String> password;
	public static volatile SingularAttribute<User, String> phoneNumber;
	public static volatile SingularAttribute<User, Account> accountAdministered;
	public static volatile SingularAttribute<User, Boolean> hasWebProfile;
	public static volatile SingularAttribute<User, Long> id;
	public static volatile SingularAttribute<User, Group> safetyGroup;
	public static volatile SingularAttribute<User, String> username;

}

