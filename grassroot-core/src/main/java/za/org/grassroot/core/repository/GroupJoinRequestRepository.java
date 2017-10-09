package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.association.GroupJoinRequest;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AssocRequestStatus;

import java.util.List;

public interface GroupJoinRequestRepository extends JpaRepository<GroupJoinRequest, Long> {
    GroupJoinRequest findOneByUid(String uid);

    GroupJoinRequest findByGroupAndRequestorAndStatus(Group group, User requestor, AssocRequestStatus status);

    List<GroupJoinRequest> findByGroupJoinApproverAndStatus(User approver, AssocRequestStatus status, Sort sort);

    List<GroupJoinRequest> findByRequestorAndStatus(User requestor, AssocRequestStatus status, Sort sort);

    List<GroupJoinRequest> findByRequestorAndStatusAndGroupIn(User requestor, AssocRequestStatus status, List<Group> groups);

    List<GroupJoinRequest> findByGroupAndStatus(Group group, AssocRequestStatus status);

}
