package za.org.grassroot.webapp.controller.ussd.group;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.ussd.UssdSupport;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Locale;

import static za.org.grassroot.webapp.enums.USSDSection.HOME;

@Service
public class UssdGroupJoinServiceImpl implements UssdGroupJoinService {
	private final Logger log = LoggerFactory.getLogger(UssdGroupJoinServiceImpl.class);

	private final UserManagementService userManager;
	private final GroupBroker groupBroker;
	private final UssdSupport ussdSupport;

	public UssdGroupJoinServiceImpl(UserManagementService userManager, GroupBroker groupBroker, UssdSupport ussdSupport) {
		this.userManager = userManager;
		this.groupBroker = groupBroker;
		this.ussdSupport = ussdSupport;
	}

	@Override
	@Transactional
	public Request processSetJoinTopics(String inputNumber, String groupUid, String topic) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		groupBroker.setMemberJoinTopics(user.getUid(), groupUid, user.getUid(), Collections.singletonList(topic));
		String prompt = ussdSupport.getMessage(HOME, ussdSupport.startMenu, "prompt.group.topics.set", topic, user);
		return ussdSupport.menuBuilder(ussdSupport.setUserProfile(user, prompt));
	}

	@Override
	@Transactional
	public Request processSetUserProfileMenu(String inputNumber, String field, Province province, Locale language, String name) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);

		if ("PROVINCE".equals(field)) {
			userManager.updateUserProvince(user.getUid(), province);
		} else if ("LANGUAGE".equals(field)) {
			userManager.updateUserLanguage(user.getUid(), language, UserInterfaceType.USSD);
		} else if ("NAME".equals(field) && !StringUtils.isEmpty(name) && name.length() > 1) {
			userManager.updateDisplayName(user.getUid(), user.getUid(), name);
		}

		String prompt = ussdSupport.getMessage("home.start.profile.step", user);
		User updatedUser = userManager.load(user.getUid());
		log.info("updated user, now set as : {}", updatedUser);
		return ussdSupport.menuBuilder(ussdSupport.setUserProfile(updatedUser, prompt));
	}
}
