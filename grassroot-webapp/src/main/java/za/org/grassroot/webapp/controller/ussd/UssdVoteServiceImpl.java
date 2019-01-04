package za.org.grassroot.webapp.controller.ussd;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.enums.EventSpecialForm;
import za.org.grassroot.services.task.VoteBroker;
import za.org.grassroot.services.user.UserManager;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;

@Service
public class UssdVoteServiceImpl implements UssdVoteService {
	private final UssdSupport ussdSupport;
	private final VoteBroker voteBroker;
	private final UserManager userManager;

	public UssdVoteServiceImpl(UssdSupport ussdSupport, VoteBroker voteBroker, UserManager userManager) {
		this.ussdSupport = ussdSupport;
		this.voteBroker = voteBroker;
		this.userManager = userManager;
	}

	@Override
	public USSDMenu assembleVoteMenu(User user, Vote vote) {
		final String[] promptFields = new String[]{vote.getAncestorGroup().getName(""),
				vote.getAncestorGroup().getMembership(vote.getCreatedByUser()).getDisplayName(),
				vote.getName()};

		final String prompt = EventSpecialForm.MASS_VOTE.equals(vote.getSpecialForm()) ? UssdSupport.promptKey + "-vote-mass" : UssdSupport.promptKey + "-vote";
		USSDMenu openingMenu = new USSDMenu(ussdSupport.getMessage(USSDSection.HOME, UssdSupport.startMenu, prompt, promptFields, user));

		if (vote.getVoteOptions().isEmpty()) {
			addYesNoOptions(vote, user, openingMenu);
		} else {
			addVoteOptions(vote, openingMenu);
		}

		if (!StringUtils.isEmpty(vote.getDescription())) {
			openingMenu.addMenuOption(UssdSupport.voteMenus + "description?voteUid=" + vote.getUid() + "&back=respond",
					ussdSupport.getMessage("home.generic.moreinfo", user));
		}

		return openingMenu;
	}

	@Override
	@Transactional
	public Request showVoteDescription(String inputNumber, String voteUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		Vote vote = voteBroker.load(voteUid);

		USSDMenu menu = new USSDMenu(vote.getDescription());
		if (vote.getVoteOptions().isEmpty()) {
			addYesNoOptions(vote, user, menu);
		} else if (String.join("X. ", vote.getVoteOptions()).length() + 3 + vote.getDescription().length() < 160) {
			addVoteOptions(vote, menu);
		}

		if (!menu.hasOptions() || menu.getMenuCharLength() < 160) {
			menu.addMenuOption(UssdSupport.voteMenus + "respond?voteUid=" + vote.getUid(), ussdSupport.getMessage("options.back", user));
		}

		if (menu.getMenuCharLength() < 160) {
			menu.addMenuOption("start_force", ussdSupport.getMessage("options.skip", user));
		}

		return ussdSupport.menuBuilder(menu);
	}

	private void addYesNoOptions(Vote vote, User user, USSDMenu menu) {
		final String optionMsgKey = UssdSupport.voteKey + "." + UssdSupport.optionsKey;
		final String voteUri = UssdSupport.voteMenus + "record?voteUid=" + vote.getUid() + "&response=";
		menu.addMenuOption(voteUri + "YES", ussdSupport.getMessage(optionMsgKey + "yes", user));
		menu.addMenuOption(voteUri + "NO", ussdSupport.getMessage(optionMsgKey + "no", user));
		menu.addMenuOption(voteUri + "ABSTAIN", ussdSupport.getMessage(optionMsgKey + "abstain", user));
	}

	private void addVoteOptions(Vote vote, USSDMenu menu) {
		final String voteUri = UssdSupport.voteMenus + "record?voteUid=" + vote.getUid() + "&response=";
		vote.getVoteOptions().forEach(o -> {
			menu.addMenuOption(voteUri + USSDUrlUtil.encodeParameter(o), o);
		});
	}
}
