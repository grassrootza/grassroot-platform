package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupTokenCode;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.GroupTokenCodeRepository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Random;

/**
 * Created by luke on 2015/08/30.
 */
@Service
public class GroupTokenManager implements GroupTokenService {

    private Logger log = LoggerFactory.getLogger(GroupTokenManager.class);

    @Autowired
    GroupTokenCodeRepository groupTokenCodeRepository;
    @Autowired
    GroupRepository groupRepository;
    @Autowired
    UserManagementService userManager;

    @Override
    public GroupTokenCode generateGroupCode(Long groupId, String inputNumber) {

        Group groupForToken = groupRepository.findOne(groupId);
        User creatingUser = userManager.findByInputNumber(inputNumber);
        return generateGroupCode(groupForToken, creatingUser);
    }

    @Override
    public GroupTokenCode generateGroupCode(Group group, User creatingUser) {
        // todo: check that the creating user is part of the group and has permission
        // todo: first check if there is an open token on this group and if so, return it or raise and catch exception
        // todo: use the groupId in some way in generating the token, to speed up / simplify look up later
        // todo: implement a way to keep tokens unique, but also to flush expired tokens regularly

        String code = String.valueOf(1000 + new Random().nextInt(9999));
        GroupTokenCode newToken = new GroupTokenCode(group, creatingUser, code);
        newToken = groupTokenCodeRepository.save(newToken);
        return newToken;
    }

    @Override
    public GroupTokenCode generateGroupCode(Group group, User creatingUser, Integer numberOfDays) {

        // todo: implement Java 8 LocalDateTime properly in domain (replacing / augmenting Timestamp)

        GroupTokenCode newGroupToken = generateGroupCode(group, creatingUser);
        LocalDateTime expiryDateTime = LocalDateTime.now().plusDays(numberOfDays);
        newGroupToken.setExpiryDateTime(Timestamp.valueOf(expiryDateTime));
        newGroupToken = groupTokenCodeRepository.save(newGroupToken);
        return newGroupToken;

    }

    @Override
    public GroupTokenCode generateGroupCode(Group group, User creatingUser, LocalDateTime expiryDateTime) {
        GroupTokenCode newGroupToken = generateGroupCode(group, creatingUser);
        newGroupToken.setExpiryDateTime(Timestamp.valueOf(expiryDateTime));
        newGroupToken = groupTokenCodeRepository.save(newGroupToken);
        return newGroupToken;
    }

    @Override
    public GroupTokenCode extendGroupCode(String code, Integer daysValid) {
        // todo: flush the table regularly so codes don't repeat, and add a unique key to the table
        GroupTokenCode groupToken = groupTokenCodeRepository.findByCode(code);
        LocalDateTime extendedDateTime = groupToken.getExpiryDateTime().toLocalDateTime().plusDays(daysValid);
        groupToken.setExpiryDateTime(Timestamp.valueOf(extendedDateTime));
        groupToken = groupTokenCodeRepository.save(groupToken);
        return groupToken;
    }

    /*
    Given how often we may check if the user has passed a group code, need a very fast / pared down query to check if
    the token exists and find its group.
     */
    @Override
    public boolean doesGroupCodeExist(String code) {
        GroupTokenCode returnedCode = groupTokenCodeRepository.findByCode(code);
        return (returnedCode != null);
    }

    @Override
    public boolean doesGroupCodeExistByGroupId(Long groupId) {
        Group groupToCheck = groupRepository.findOne(groupId);
        GroupTokenCode returnedCode = groupToCheck.getGroupTokenCode();
        return (returnedCode != null &&
                returnedCode.getExpiryDateTime().after(new Timestamp(Calendar.getInstance().getTimeInMillis())));
    }

    @Override
    public Long getGroupIdFromToken(String code) {
        GroupTokenCode groupToken = groupTokenCodeRepository.findByCode(code);
        return groupToken.getGroup().getId();
    }

    @Override
    public Group getGroupFromToken(String code) {
        GroupTokenCode groupToken = groupTokenCodeRepository.findByCode(code);
        return groupToken.getGroup();
    }

    @Override
    public boolean doesGroupCodeMatch(String code, Long groupId) {
        // todo: check that the token has not expired yet
        GroupTokenCode groupToken = groupTokenCodeRepository.findByCode(code);
        return (groupToken != null && groupId == groupToken.getGroup().getId());
    }

    @Override
    public boolean invalidateGroupToken(Long groupId, User creatingUser, String code) {
        // todo: throw an exception if the user doesn't have rights, or the token doesn't exist/is expired already, vs just true/false
        // if (!doesGroupCodeMatch(code, groupId)) { return false; }

        GroupTokenCode groupTokenCode = groupTokenCodeRepository.findByCode(code);
        // if (!creatingUser.equals(groupTokenCode.getCreatingUser())) { return false; }

        groupTokenCode.setCode(null); // maybe better to delete the entity?
        groupTokenCodeRepository.save(groupTokenCode);
        return true;
    }

}
