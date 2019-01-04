package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

public interface UssdVoteService {
	USSDMenu assembleVoteMenu(User user, Vote vote);

	Request showVoteDescription(String inputNumber, String voteUid) throws URISyntaxException;
}
