package za.org.grassroot.webapp.model.rest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import za.org.grassroot.core.domain.GroupJoinRequest;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Created by luke on 2016/07/09.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupJoinRequestDTO implements Comparable<GroupJoinRequestDTO> {

	private String requestUid;
	private String requestorName;
	private String requestorNumber;

	private String groupUid;
	private String groupName;

	private String requestDescription;

	@JsonIgnore
	private Instant createdDateTime;
	private String createdDateTimeISO;

	public GroupJoinRequestDTO(GroupJoinRequest request) {
		this.requestUid = request.getUid();
		this.requestorName = request.getRequestor().nameToDisplay();
		this.requestDescription = request.getRequestor().getPhoneNumber();
		this.groupUid = request.getGroup().getUid();
		this.groupName = request.getGroup().getGroupName();
		this.requestDescription = request.getDescription();
		this.createdDateTime = request.getCreationTime();
		this.createdDateTimeISO = DateTimeUtil.convertToUserTimeZone(request.getCreationTime(), DateTimeUtil.getSAST())
				.format(DateTimeUtil.getPreferredRestFormat());
	}

	@Override
	public int compareTo(GroupJoinRequestDTO groupJoinRequestDTO) {
		return createdDateTime.compareTo(groupJoinRequestDTO.createdDateTime);
	}

	public String getRequestUid() {
		return requestUid;
	}

	public String getRequestorName() {
		return requestorName;
	}

	public String getRequestorNumber() {
		return requestorNumber;
	}

	public String getGroupUid() {
		return groupUid;
	}

	public String getGroupName() {
		return groupName;
	}

	public String getRequestDescription() {
		return requestDescription;
	}

	public Instant getCreatedDateTime() {
		return createdDateTime;
	}

	public String getCreatedDateTimeISO() {
		return createdDateTimeISO;
	}
}
