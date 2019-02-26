package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.dto.UserMinimalProjection;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.geo.AddressBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDCampaignConstants;

import java.net.URISyntaxException;
import java.util.*;

import static za.org.grassroot.webapp.controller.ussd.UssdSupport.userMenus;

@Service
public class UssdCampaignServiceImpl implements UssdCampaignService {
	private final Logger log = LoggerFactory.getLogger(UssdCampaignServiceImpl.class);

	private final CampaignBroker campaignBroker;
	private final UserManagementService userManager;
	private final AddressBroker addressBroker;
	private final UssdSupport ussdSupport;

	// needed because of Java locale hell
	private final Map<String, Locale> localeMap = constructLocaleMap();

	private static Map<String, Locale> constructLocaleMap() {
		// due to Java Locale hell
		Map<String, Locale> localeMap = new HashMap<>();
		localeMap.put("eng", new Locale("en"));
		localeMap.put("zul", new Locale("zu"));
		localeMap.put("xho", new Locale("xh"));
		localeMap.put("afr", new Locale("af"));
		localeMap.put("sot", new Locale("st"));
		return localeMap;
	}

	@Value("${grassroot.campaigns.redirect.main:false}")
	// controls whether to direct people who sign campaign into general GR capabalities
	private boolean redirectCampaignCompletionToMain = false;

	public UssdCampaignServiceImpl(CampaignBroker campaignBroker, UserManagementService userManager, AddressBroker addressBroker, UssdSupport ussdSupport) {
		this.campaignBroker = campaignBroker;
		this.userManager = userManager;
		this.addressBroker = addressBroker;
		this.ussdSupport = ussdSupport;
	}

	private String iso3CountryCodeToIso2CountryCode(String iso3CountryCode) {
		log.debug("iso 3 country code: {}", iso3CountryCode);
		return localeMap.getOrDefault(iso3CountryCode, Locale.ENGLISH).getLanguage();
	}

	@Override
	@Transactional
	public Request handleUserSetKanguageForCampaign(String inputNumber, String campaignUid, String languageCode) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		if (!user.hasLanguage()) {
			userManager.updateUserLanguage(user.getUid(), new Locale(languageCode), UserInterfaceType.USSD);
		}
		CampaignMessage campaignMessage = campaignBroker.getOpeningMessage(campaignUid, new Locale(languageCode), UserInterfaceType.USSD, null);
		USSDMenu ussdMenu = buildCampaignUSSDMenu(campaignMessage);
		return ussdSupport.menuBuilder(ussdMenu);
	}

	@Override
	@Transactional
	public Request handleMoreInfoRequest(String inputNumber, String messageUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
		return ussdSupport.menuBuilder(buildCampaignUSSDMenu(message));
	}

	@Override
	@Transactional
	public Request handleTagMeRequest(String inputNumber, String messageUid, String parentMsgUid) throws URISyntaxException {
		final CampaignMessage message = tagMembership(inputNumber, messageUid, parentMsgUid);
		return ussdSupport.menuBuilder(buildCampaignUSSDMenu(message));
	}

	@Override
	@Transactional
	public Request handleJoinMasterGroupRequest(String inputNumber, String messageUid, String campaignUid) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalByMsisdn(inputNumber);
		Locale locale;
		String promptStart;
		if (campaignUid == null) {
			CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
			campaignUid = message.getCampaign().getUid();
			locale = message.getLocale();
			promptStart = message.getMessage() + (StringUtils.isEmpty(message.getMessage()) ? "" : ". ");
			log.debug("prompt start: {}, message : {}", promptStart, message.getMessage());
		} else {
			promptStart = ussdSupport.getMessage("campaign.joined.generic", user);
			locale = user.getLocale();
		}
		Campaign campaign = campaignBroker.addUserToCampaignMasterGroup(campaignUid, user.getUid(), UserInterfaceType.USSD);
		USSDMenu ussdMenu = topicsOrFinalOptionsMenu(campaign, user, promptStart, locale);
		return ussdSupport.menuBuilder(ussdMenu);
	}

	@Override
	@Transactional
	public Request handleSetUserJoinTopic(String inputNumber, String campaignUid, String topic) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalByMsisdn(inputNumber);
		Campaign campaign = campaignBroker.load(campaignUid);
		campaignBroker.setUserJoinTopic(campaignUid, user.getUid(), topic, UserInterfaceType.USSD);
		return ussdSupport.menuBuilder(processFinalOptionsMenu(campaign, user, "", user.getLocale()));
	}

	@Override
	@Transactional
	public Request handleProvinceRequest(String inputNumber, String campaignUid, Province province) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalByMsisdn(inputNumber);
		user = userManager.updateUserProvince(user.getUid(), province);
		Campaign campaign = campaignBroker.load(campaignUid);
		return ussdSupport.menuBuilder(processFinalOptionsMenu(campaign, user, "", user.getLocale()));
	}

	@Override
	@Transactional
	public Request handleNameRequest(String inputNumber, String campaignUid, String enteredName) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalByMsisdn(inputNumber);
		user = userManager.updateDisplayName(user.getUid(), user.getUid(), enteredName);
		Campaign campaign = campaignBroker.load(campaignUid);
		return ussdSupport.menuBuilder(processFinalOptionsMenu(campaign, user, "", user.getLocale()));
	}

	@Override
	public Request handleExitRequest(String inputNumber, String messageUid, String campaignUid) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalByMsisdn(inputNumber);
		if (campaignUid == null) {
			CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
			USSDMenu ussdMenu = buildCampaignUSSDMenu(message);
			return ussdSupport.menuBuilder(ussdMenu);
		} else {
			USSDMenu ussdMenu = genericPositiveExit(campaignUid, user, user.getLocale());
			return ussdSupport.menuBuilder(ussdMenu);
		}
	}

	@Override
	public Request handleSharePrompt(String inputNumber, String messageUid) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalByMsisdn(inputNumber);
		CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
		return ussdSupport.menuBuilder(buildCampaignUSSDMenu(message));
	}

	@Override
	public Request handleSignPetitionRequest(String inputNumber, String messageUid) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalByMsisdn(inputNumber);
		CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
		campaignBroker.signPetition(message.getCampaign().getUid(), user.getUid(), UserInterfaceType.USSD);
		final String promptStart = message.getMessage() + (StringUtils.isEmpty(message.getMessage()) ? "" : ". ");
		final USSDMenu ussdMenu = joinGroupOrFinalOptionsMenu(message.getCampaign(), user, promptStart, message.getLocale());
		return ussdSupport.menuBuilder(ussdMenu);
	}

	@Override
	@Transactional
	public Request handleShareDo(String inputNumber, String userInput, String campaignUid) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalByMsisdn(inputNumber);
		final String shareNumber = userInput.trim();
		if (!PhoneNumberUtil.testInputNumber(shareNumber)) {
			final String prompt = ussdSupport.getMessage("campaign.share.invalid", new String[]{shareNumber}, user);
			return ussdSupport.menuBuilder(new USSDMenu(prompt, "campaign/share/do?campaignUid=" + campaignUid));
		} else {
			final String shareDefault = ussdSupport.getMessage("campaign.share.send.generic", user);
			campaignBroker.sendShareMessage(campaignUid, user.getUid(), shareNumber, shareDefault, UserInterfaceType.USSD);
			return ussdSupport.menuBuilder(genericPositiveExit(campaignUid, user, user.getLocale()));
		}
	}

	private USSDMenu topicsOrFinalOptionsMenu(Campaign campaign, UserMinimalProjection user, String promptStart, Locale locale) {
		USSDMenu menu;
		if (!campaign.getJoinTopics().isEmpty()) {
			final String prompt = promptStart + ussdSupport.getMessage("campaign.choose.topic", locale.getLanguage());
			final String urlPrefix = campaignMenus + "topic/set?campaignUid=" + campaign.getUid() + "&topic=";
			menu = new USSDMenu(prompt);
			campaign.getJoinTopics().forEach(topic -> menu.addMenuOption(urlPrefix + topic, topic));
		} else {
			menu = processFinalOptionsMenu(campaign, user, promptStart, locale);
		}
		return menu;
	}

	private USSDMenu processFinalOptionsMenu(Campaign campaign, UserMinimalProjection user, String promptStart, Locale locale) {
		USSDMenu menu;
		if (user.getProvince() == null) {
			final String prompt = promptStart + ussdSupport.getMessage("campaign.joined.province", user);
			menu = new USSDMenu(prompt, ussdSupport.provinceOptions(user, campaignMenus + "province?campaignUid="
					+ campaign.getUid() + "&province="));
		} else if (!user.hasName()) {
			final String prompt = promptStart + ussdSupport.getMessage("campaign.joined.name", user);
			menu = new USSDMenu(prompt, campaignMenus + "user/name?campaignUid=" + campaign.getUid());
		} else if (campaign.isOutboundTextEnabled() && campaign.outboundBudgetLeft() > 0
				&& !campaignBroker.hasUserShared(campaign.getUid(), user.getUid())) {
			menu = buildSharingMenu(campaign.getUid(), locale);
		} else if (!addressBroker.hasAddressOrLocation(user.getUid())) {
			final String prompt = ussdSupport.getMessage("campaign.joined.town", user);
			menu = new USSDMenu(prompt, userMenus + "town/select");
		} else {
			menu = genericPositiveExit(campaign.getUid(), user, locale);
		}
		return menu;
	}

	private CampaignMessage tagMembership(String inputNumber, String messageUid, String parentMsgUid) {
		final User user = userManager.findByInputNumber(inputNumber);
		final CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
		final CampaignMessage parentMessage = campaignBroker.loadCampaignMessage(parentMsgUid, user.getUid());

		final Campaign campaign = message.getCampaign();
		final Group masterGroup = campaign.getMasterGroup();
		if (parentMessage != null && parentMessage.getTagList() != null && parentMessage.getTagList().isEmpty()) {
			final Membership membership = user.getMembershipOptional(masterGroup)
					.orElseGet(() -> {
								// todo: VJERAN: - Is this bug in commented old line where Role's group UID is null? Test this new code!!!
								return campaign.getMasterGroup().addMember(user, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.SELF_JOINED, null);
								//                membership = new Membership(campaign.getMasterGroup(), user, new Role(GroupRole.ROLE_ORDINARY_MEMBER, null), Instant.now(), GroupJoinMethod.SELF_JOINED, null);
								//                user.getMemberships().add(membership);
								//                userManager.createUserProfile(user);
							}
					);
			for (String tag : parentMessage.getTagList()) {
				membership.addTag(tag);
			}
		}
		return message;
	}

	private USSDMenu buildCampaignUSSDMenu(CampaignMessage campaignMessage) {
		String promptMessage = campaignMessage.getMessage();
		Map<String, String> linksMap = new LinkedHashMap<>();
		campaignMessage.getNextMessages().forEach((msgUid, actionType) -> {
			String optionKey = USSDCampaignConstants.CAMPAIGN_PREFIX + actionType.name().toLowerCase();
			final String converted = iso3CountryCodeToIso2CountryCode(campaignMessage.getLocale().getLanguage());
			log.info("USSD campaign message key: {}, converted: {}", optionKey, converted);
			String option = ussdSupport.getMessage(optionKey, converted);
			String embeddedUrl = campaignMenus +
					USSDCampaignConstants.getCampaignUrlPrefixs().get(actionType) + "?" +
					USSDCampaignConstants.MESSAGE_UID_PARAMETER + msgUid;
			linksMap.put(embeddedUrl, option);
		});
		return new USSDMenu(promptMessage, linksMap);
	}

	private USSDMenu buildSharingMenu(String campaignUid, Locale locale) {
		List<CampaignMessage> campaignMessage = campaignBroker.findCampaignMessage(campaignUid,
				CampaignActionType.SHARE_PROMPT, locale, UserInterfaceType.USSD);
		final String prompt = !campaignMessage.isEmpty() ? campaignMessage.get(0).getMessage() :
				ussdSupport.getMessage("campaign.share.generic", locale.getLanguage());
		return new USSDMenu(prompt, campaignMenus + "share/do?campaignUid=" + campaignUid);
	}

	private USSDMenu genericPositiveExit(String campaignUid, UserMinimalProjection user, Locale locale) {
		log.info("inside generic positive exit ...");
		List<CampaignMessage> campaignMessage = campaignBroker.findCampaignMessage(campaignUid,
				CampaignActionType.EXIT_POSITIVE, locale, UserInterfaceType.USSD);
		log.info("found a campaign message? : {}", campaignMessage);
		USSDMenu menu = !campaignMessage.isEmpty() ? buildCampaignUSSDMenu(campaignMessage.get(0)) :
				new USSDMenu(ussdSupport.getMessage("campaign.exit_positive.generic", locale.getLanguage()));
		if (redirectCampaignCompletionToMain) {
			menu.addMenuOptions(ussdSupport.optionsHomeExit(user, false));
		} else {
			menu.setFreeText(false);
		}
		return menu;
	}

	private USSDMenu joinGroupOrFinalOptionsMenu(Campaign campaign, UserMinimalProjection user, String promptStart, Locale locale) {
		USSDMenu menu;
		if (!campaignBroker.isUserInCampaignMasterGroup(campaign.getUid(), user.getUid())) {
			final String prompt = promptStart + ussdSupport.getMessage("campaign.join.generic", locale.getLanguage());
			menu = new USSDMenu(prompt, ussdSupport.optionsYesNo(user,
					campaignMenus + USSDCampaignConstants.JOIN_MASTER_GROUP_URL + "?campaignUid=" + campaign.getUid(),
					campaignMenus + USSDCampaignConstants.EXIT_URL + "?campaignUid=" + campaign.getUid()));
		} else {
			menu = processFinalOptionsMenu(campaign, user, promptStart, locale);
		}
		return menu;
	}

}
