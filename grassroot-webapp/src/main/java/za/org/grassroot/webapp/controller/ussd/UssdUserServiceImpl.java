package za.org.grassroot.webapp.controller.ussd;

import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.UserMinimalProjection;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.integration.location.LocationInfoBroker;
import za.org.grassroot.integration.location.TownLookupResult;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.geo.AddressBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;

@Service
public class UssdUserServiceImpl implements UssdUserService {
	private final Logger log = LoggerFactory.getLogger(UssdUserServiceImpl.class);

	private static final String keyStart = "start", keyName = "name";
	private static final String keyLanguage = "language";
	private static final String keyLink = "link";
	private static final USSDSection thisSection = USSDSection.USER_PROFILE;

	private final UssdSupport ussdSupport;
	private final UserManagementService userManager;
	private final AsyncUserLogger userLogger;
	private final LocationInfoBroker locationInfoBroker;
	private final AddressBroker addressBroker;


	public UssdUserServiceImpl(UssdSupport ussdSupport, UserManagementService userManager, AsyncUserLogger userLogger, LocationInfoBroker locationInfoBroker, AddressBroker addressBroker) {
		this.ussdSupport = ussdSupport;
		this.userManager = userManager;
		this.userLogger = userLogger;
		this.locationInfoBroker = locationInfoBroker;
		this.addressBroker = addressBroker;
	}

	@Override
	@Transactional
	public Request processRenameAndStart(String inputNumber, String userName) throws URISyntaxException {
		User sessionUser = userManager.findByInputNumber(inputNumber);
		String welcomeMessage;
		if ("0".equals(userName) || "".equals(userName.trim())) {
			welcomeMessage = ussdSupport.getMessage(USSDSection.HOME, startMenu, promptKey, sessionUser);
			userLogger.recordUserLog(sessionUser.getUid(), UserLogType.USER_SKIPPED_NAME, "", UserInterfaceType.USSD);
		} else {
			userManager.updateDisplayName(sessionUser.getUid(), sessionUser.getUid(), userName.trim());
			welcomeMessage = ussdSupport.getMessage(USSDSection.HOME, startMenu, promptKey + "-rename-do", sessionUser.nameToDisplay(), sessionUser);
		}
		return ussdSupport.menuBuilder(ussdSupport.welcomeMenu(welcomeMessage, sessionUser));
	}

	@Override
	@Transactional
	public Request processUserProfile(String inputNumber) throws URISyntaxException {
		User sessionUser = userManager.findByInputNumber(inputNumber);

		USSDMenu thisMenu = new USSDMenu(ussdSupport.getMessage(thisSection, startMenu, promptKey, sessionUser));

		thisMenu.addMenuOption(userMenus + keyName, ussdSupport.getMessage(thisSection, startMenu, optionsKey + keyName, sessionUser));
		thisMenu.addMenuOption(userMenus + keyLanguage, ussdSupport.getMessage(thisSection, startMenu, optionsKey + keyLanguage, sessionUser));
		thisMenu.addMenuOption(userMenus + "town", ussdSupport.getMessage(thisSection, startMenu, optionsKey + "town", sessionUser));
		thisMenu.addMenuOption(userMenus + "email", ussdSupport.getMessage(thisSection, startMenu, optionsKey + "email", sessionUser));
		thisMenu.addMenuOption(userMenus + keyLink + doSuffix, ussdSupport.getMessage(thisSection, startMenu, optionsKey + keyLink, sessionUser));
		thisMenu.addMenuOption(keyStart, ussdSupport.getMessage(thisSection, startMenu, optionsKey + "back", sessionUser));

		return ussdSupport.menuBuilder(thisMenu);
	}

	@Override
	@Transactional
	public Request processUserDisplayName(String inputNumber) throws URISyntaxException {
		USSDMenu thisMenu = new USSDMenu("", userMenus + keyName + doSuffix);
		User sessionUser = userManager.findByInputNumber(inputNumber);

		if (sessionUser.hasName()) {
			thisMenu.setPromptMessage(ussdSupport.getMessage(thisSection, keyName, promptKey + ".named", sessionUser.getDisplayName(), sessionUser));
		} else {
			thisMenu.setPromptMessage(ussdSupport.getMessage(thisSection, keyName, promptKey + ".unnamed", sessionUser));
		}

		return ussdSupport.menuBuilder(thisMenu);
	}

	@Override
	@Transactional
	public Request processUserChangeName(String inputNumber, String newName) throws URISyntaxException {
		User sessionUser = userManager.findByInputNumber(inputNumber);
		userManager.updateDisplayName(sessionUser.getUid(), sessionUser.getUid(), newName.trim());
		return ussdSupport.menuBuilder(new USSDMenu(ussdSupport.getMessage(thisSection, keyName + doSuffix, promptKey, sessionUser), ussdSupport.optionsHomeExit(sessionUser, false)));
	}

	@Override
	@Transactional
	public Request processUserPromptLanguage(String inputNumber) throws URISyntaxException {
		User sessionUser;
		try {
			sessionUser = userManager.findByInputNumber(inputNumber);
		} catch (NoSuchElementException e) {
			return ussdSupport.noUserError;
		}

		USSDMenu thisMenu = new USSDMenu(ussdSupport.getMessage(thisSection, keyLanguage, promptKey, sessionUser));

		for (Map.Entry<String, String> entry : BaseController.getImplementedLanguages().entrySet()) {
			thisMenu.addMenuOption(userMenus + keyLanguage + doSuffix + "?language=" + entry.getKey(), entry.getValue());
		}

		return ussdSupport.menuBuilder(thisMenu);
	}

	@Override
	@Transactional
	public Request processUserChangeLanguage(String inputNumber, String language) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		user.setLanguageCode(language); // so next prompt shows up without needing repeat DB query
		userManager.updateUserLanguage(user.getUid(), new Locale(language), UserInterfaceType.USSD);

		return ussdSupport.menuBuilder(new USSDMenu(ussdSupport.getMessage(thisSection, keyLanguage + doSuffix, promptKey, user), ussdSupport.optionsHomeExit(user, false)));
	}

	@Override
	@Transactional
	public Request processUserSendAndroidLink(String inputNumber) throws URISyntaxException {
		User sessionUser;
		try {
			sessionUser = userManager.findByInputNumber(inputNumber);
			userManager.sendAndroidLinkSms(sessionUser.getUid());
		} catch (NoSuchElementException e) {
			return ussdSupport.noUserError;
		}
		return ussdSupport.menuBuilder(new USSDMenu(ussdSupport.getMessage(thisSection, keyLink + doSuffix, promptKey, sessionUser),
				ussdSupport.optionsHomeExit(sessionUser, false)));
	}

	@Override
	@Transactional
	public Request processAlterEmailPrompt(String inputNumber) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		final String prompt = !user.hasEmailAddress() ? ussdSupport.getMessage("user.email.prompt.none", user) :
				ussdSupport.getMessage(thisSection, "email", "prompt.set", user.getEmailAddress(), user);
		return ussdSupport.menuBuilder(new USSDMenu(prompt, userMenus + "email/set"));
	}

	@Override
	@Transactional
	public Request processSetEmail(String inputNumber, String email) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		USSDMenu menu;
		if (!EmailValidator.getInstance().isValid(email)) {
			menu = new USSDMenu(ussdSupport.getMessage("user.email.prompt.invalid", user), userMenus + "email/set");
		} else if (userManager.emailTaken(user.getUid(), email)) {
			menu = new USSDMenu(ussdSupport.getMessage("user.email.prompt.taken", user), userMenus + "email/set");
		} else {
			userManager.updateEmailAddress(user.getUid(), user.getUid(), email);
			menu = new USSDMenu(ussdSupport.getMessage("user.email.prompt.done", user), ussdSupport.optionsHomeExit(user, false));
		}
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processTownPrompt(String inputNumber) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalByMsisdn(inputNumber);
		final String prompt = ussdSupport.getMessage(thisSection, "town", "prompt", user);
		return ussdSupport.menuBuilder(new USSDMenu(prompt, userMenus + "town/select"));
	}

	@Override
	@Transactional
	public Request processTownOptions(String inputNumber, String userInput) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalByMsisdn(inputNumber);
		if ("0".equals(userInput.trim())) {
			return ussdSupport.menuBuilder(new USSDMenu(ussdSupport.getMessage("campaign.exit_positive.generic", user)));
		}

		final List<TownLookupResult> placeDescriptions = locationInfoBroker.lookupPostCodeOrTown(userInput, user.getProvince());
		if (placeDescriptions == null || placeDescriptions.isEmpty()) {
			final String prompt = ussdSupport.getMessage(thisSection, "town", "none.prompt", user);
			return ussdSupport.menuBuilder(new USSDMenu(prompt, userMenus + "town/select"));
		} else if (placeDescriptions.size() == 1) {
			final String prompt = ussdSupport.getMessage(thisSection, "town", "one.prompt", placeDescriptions.get(0).getDescription(), user);
			USSDMenu menu = new USSDMenu(prompt);
			menu.addMenuOption(userMenus + "town/confirm?placeId=" + placeDescriptions.get(0).getPlaceId(), ussdSupport.getMessage("options.yes", user));
			menu.addMenuOption(userMenus + "town", ussdSupport.getMessage("options.no", user));
			return ussdSupport.menuBuilder(menu);
		} else {
			final String prompt = ussdSupport.getMessage(thisSection, "town", "many.prompt", user);
			final USSDMenu menu = new USSDMenu(prompt);
			menu.addMenuOptions(placeDescriptions.stream().collect(Collectors.toMap(
					lookup -> userMenus + "town/confirm?placeId=" + USSDUrlUtil.encodeParameter(lookup.getPlaceId()),
					TownLookupResult::getDescription)));
			return ussdSupport.menuBuilder(menu);
		}
	}

	@Override
	@Transactional
	public Request processTownConfirm(String inputNumber, String placeId) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalByMsisdn(inputNumber);
		addressBroker.setUserAreaFromUSSD(user.getUid(), placeId, LocationSource.TOWN_LOOKUP, true);
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage("user.town.updated.prompt", user));
		menu.addMenuOptions(ussdSupport.optionsHomeExit(user, true));
		return ussdSupport.menuBuilder(menu);
	}
}
