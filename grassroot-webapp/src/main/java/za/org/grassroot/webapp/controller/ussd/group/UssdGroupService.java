package za.org.grassroot.webapp.controller.ussd.group;

import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

public interface UssdGroupService {
	String
			existingGroupMenu = "menu",
			advancedGroupMenu = "advanced",
			createGroupMenu = "create",
			closeGroupToken = "create-token",
			createGroupAddNumbers = "add-numbers",
			approveUser = "approve",
			rejectUser = "reject",
			addMemberPrompt = "addnumber", // probably should rename this to prevent confusion w/ above
			unsubscribePrompt = "unsubscribe";

	USSDSection thisSection = USSDSection.GROUP_MANAGER;

	Request processGroupPaginationHelper(String inputNumber, String prompt, Integer pageNumber, String existingUri, USSDSection section, String newUri) throws URISyntaxException;

	Request processGroupList(String inputNumber, boolean interrupted) throws URISyntaxException;

	Request processGroupMenu(String inputNumber, String groupUid) throws URISyntaxException;

	Request processCreatePrompt(String inputNumber) throws URISyntaxException;

	Request processCreateGroupWithName(String inputNumber, String groupName, boolean interrupted, String groupUid) throws URISyntaxException;

	Request processSetGroupPublic(String inputNumber, String groupUid, boolean useLocation) throws URISyntaxException;

	Request processSetGroupPrivate(String inputNumber, String groupUid) throws URISyntaxException;

	Request processCloseGroupTokenDo(String inputNumber, String groupUid) throws URISyntaxException;

	Request processCreateGroupAddNumbersOpeningPrompt(String inputNumber, String groupUid) throws URISyntaxException;

	Request processAddNumbersToNewlyCreatedGroup(String inputNumber, String groupUid, String userInput, String priorInput) throws URISyntaxException;

	Request processApproveUser(String inputNumber, String requestUid) throws URISyntaxException;

	Request processRejectUser(String inputNumber, String requestUid) throws URISyntaxException;

	Request processAddNumberInput(String inputNumber, String groupUid) throws URISyntaxException;

	Request processAddNumberToGroup(String inputNumber, String groupUid, String numberToAdd) throws URISyntaxException;

	Request processUnsubscribeConfirm(String inputNumber, String groupUid) throws URISyntaxException;

	Request processUnsubscribeDo(String inputNumber, String groupUid) throws URISyntaxException;

	Request processAdvancedGroupMenu(String inputNumber, String groupUid) throws URISyntaxException;
}
