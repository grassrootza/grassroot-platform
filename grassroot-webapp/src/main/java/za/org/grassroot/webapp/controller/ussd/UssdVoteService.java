package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.VoteTime;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

public interface UssdVoteService {
	USSDMenu assembleVoteMenu(User user, Vote vote);

	Request processShowVoteDescription(String inputNumber, String voteUid) throws URISyntaxException;

	Request processRespondToVote(String inputNumber, String voteUid) throws URISyntaxException;

	Request processVoteAndWelcome(String inputNumber, String voteUid, String response) throws URISyntaxException;

	Request processVoteSubject(String msisdn, String requestUid) throws URISyntaxException;

	Request processVoteType(String msisdn, String request, String requestUid) throws URISyntaxException;

	Request processYesNoSelectGroup(String msisdn, String requestUid) throws URISyntaxException;

	Request processSelectTime(String msisdn, String requestUid, String groupUid) throws URISyntaxException;

	Request processInitiateMultiOption(String msisdn, String requestUid) throws URISyntaxException;

	Request processAddVoteOption(String msisdn, String requestUid, String request, String priorInput) throws URISyntaxException;

	Request processCustomVotingTime(String msisdn, String requestUid) throws URISyntaxException;

	Request processConfirmVoteSend(String msisdn, String requestUid, String request, String priorInput, String field, VoteTime time, Boolean interrupted) throws URISyntaxException;

	Request processVoteSendResetTime(String inputNumber, String requestUid) throws URISyntaxException;

	Request processVoteSendDo(String inputNumber, String requestUid) throws URISyntaxException;

	Optional<USSDMenu> processPossibleMassVote(User user, Group group);

	Request processKnownMassVote(String inputNumber, String voteUid) throws URISyntaxException;

	Request processMassVoteLanguageSelection(String inputNumber, String voteUid, Locale language) throws URISyntaxException;

	Request processMassVoteResponse(String inputNumber, String voteUid, String response, Locale language, Integer voteCount) throws URISyntaxException;
}
