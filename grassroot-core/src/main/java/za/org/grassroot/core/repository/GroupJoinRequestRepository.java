package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupJoinRequest;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.GroupJoinRequestStatus;

import java.util.List;

public interface GroupJoinRequestRepository extends JpaRepository<GroupJoinRequest, Long> {
    GroupJoinRequest findOneByUid(String uid);

    GroupJoinRequest findByGroupAndRequestorAndStatus(Group group, User requestor, GroupJoinRequestStatus status);

    List<GroupJoinRequest> findByGroupJoinApproverAndStatus(User approver, GroupJoinRequestStatus status, Sort sort);

    List<GroupJoinRequest> findByRequestorAndStatus(User requestor, GroupJoinRequestStatus status, Sort sort);

    List<GroupJoinRequest> findByRequestorAndStatusAndGroupIn(User requestor, GroupJoinRequestStatus status, List<Group> groups);

}
