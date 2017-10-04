package za.org.grassroot.webapp.model.rest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.association.GroupJoinRequest;
import za.org.grassroot.core.enums.AssocRequestStatus;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.webapp.enums.JoinReqType;

import java.time.Instant;

/**
 * Created by luke on 2016/07/09.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class GroupJoinRequestDTO implements Comparable<GroupJoinRequestDTO> {

	private String requestUid;

	private String requestorName;
	private String requestorNumber;

	private String groupUid;
	private String groupName;

	private String requestDescription;

	private JoinReqType joinReqType;
	private AssocRequestStatus status;

	@JsonIgnore
	private Instant createdDateTime;

	private long createdDateTimeEpochMillis;
	private String createdDateTimeISO;

	public GroupJoinRequestDTO(GroupJoinRequest request, User user) {
		this.requestUid = request.getUid();
		this.requestorName = request.getRequestor().nameToDisplay();
		this.requestorNumber = PhoneNumberUtil.invertPhoneNumber(request.getRequestor().getPhoneNumber(), " ");
		this.requestDescription = request.getRequestor().getPhoneNumber();
		this.groupUid = request.getGroup().getUid();
		this.groupName = request.getGroup().getGroupName();
		this.requestDescription = request.getDescription();
		this.createdDateTime = request.getCreationTime();
		this.createdDateTimeEpochMillis = request.getCreationTime().toEpochMilli();
		this.createdDateTimeISO = DateTimeUtil.convertToUserTimeZone(request.getCreationTime(), DateTimeUtil.getSAST())
				.format(DateTimeUtil.getPreferredRestFormat());
		this.status = request.getStatus();
		this.joinReqType = request.getRequestor().equals(user) ? JoinReqType.SENT_REQUEST : JoinReqType.RECEIVED_REQUEST;
	}

	@Override
	public int compareTo(GroupJoinRequestDTO groupJoinRequestDTO) {
		return createdDateTime.compareTo(groupJoinRequestDTO.createdDateTime);
	}

}
