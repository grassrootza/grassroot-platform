package za.org.grassroot.core.domain;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public interface AssignedMembersContainer extends UidIdentifiableEntity {
	Group getGroup();

	/**
	 * This is just a way to get handle of internal JPA/Hibernate assigned member collection.
	 * It should not be of public visibility, but unfortunately, java interfaces cannot specify
	 * non-public methods so care has to be taken not to use it from client layers (such as controllers)
	 * which .should use normal collection getter that returns defensive copy.
	 *
	 * @return direct
	 */
	Set<User> fetchAssignedMembersCollection();

	void putAssignedMembersCollection(Set<User> assignedMembersCollection);

	/**
	 * This is method that should be used by client layers
	 * @return assigned members
	 */
	default Set<User> getAssignedMembers() {
		if (fetchAssignedMembersCollection() == null) {
			putAssignedMembersCollection(new HashSet<>());
		}
		return new HashSet<>(fetchAssignedMembersCollection());
	}

	default boolean isAllGroupMembersAssigned() {
		return getAssignedMembers().isEmpty();
	}

	/**
	 * Assigns new members to thsic container. Returns set of users that were added to the container
	 * (some maybe have already been added).
	 *
	 * @param memberUids set of member UIDs; these have to exist in belonging group
	 * @return set of newly assigned members
	 */
	default Set<User> assignMembers(Set<String> memberUids) {
		Objects.requireNonNull(memberUids);

		Group group = getGroup();

		Map<String, User> membersByUid = group.getMembers().stream()
				.collect(Collectors.toMap(User::getUid, member -> member));

		Set<User> existingMembers = fetchAssignedMembersCollection();

		Set<User> membersToAssign = memberUids.stream()
				.map(uid -> {
					User member = membersByUid.get(uid);
					if (member == null) {
						throw new IllegalArgumentException("There is no member with UID " + uid + " to be assigned to "
								+ this + " which belongs to in group " + group);
					}
					return member;
				})
				.filter(member -> !existingMembers.contains(member))
				.collect(Collectors.toSet());

		existingMembers.addAll(membersToAssign);

		return membersToAssign;
	}

	/**
	 * Removes specified members from assigned set. Returns set of users that we're actually removed.
	 *
	 * @param memberUids
	 * @return set of removed members
	 */
	default Set<User> removeAssignedMembers(Set<String> memberUids) {
		Objects.requireNonNull(memberUids);

		Set<User> existingMembers = fetchAssignedMembersCollection();
		Set<User> removedMembers = existingMembers.stream()
				.filter(member -> memberUids.contains(member.getUid()))
				.collect(Collectors.toSet());
		existingMembers.removeAll(removedMembers);

		return removedMembers;
	}
}
