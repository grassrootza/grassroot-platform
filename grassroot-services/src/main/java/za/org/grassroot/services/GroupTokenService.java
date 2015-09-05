package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupTokenCode;
import za.org.grassroot.core.domain.User;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Created by luke on 2015/08/30.
 */
public interface GroupTokenService {

    GroupTokenCode generateGroupCode(Long groupId, String inputNumber);

    GroupTokenCode generateGroupCode(Group group, User creatingUser);

    GroupTokenCode generateGroupCode(Group group, User creatingUser, Integer numberOfDays);

    GroupTokenCode generateGroupCode(Group group, User creatingUser, LocalDateTime expiryDateTime);

    GroupTokenCode extendGroupCode(String code, Integer daysValid);

    boolean doesGroupCodeExist(String code);

    boolean doesGroupCodeExistByGroupId(Long groupId);

    Long getGroupIdFromToken(String code);

    Group getGroupFromToken(String code);

    boolean doesGroupCodeMatch(String code, Long groupId);

    boolean invalidateGroupToken(Long groupId, User creatingUser, String code);

}
