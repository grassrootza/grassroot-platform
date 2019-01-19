package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.UserMinimalProjection;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.integration.location.LocationInfoBroker;
import za.org.grassroot.integration.location.TownLookupResult;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UssdGeoApiServiceImpl implements UssdGeoApiService {
	private static final String REL_PATH = "geo";

	private final Logger log = LoggerFactory.getLogger(UssdGeoApiServiceImpl.class);

	private final UssdSupport ussdSupport;
	private final LocationInfoBroker locationInfoBroker;
	private final AsyncUserLogger userLogger;
	private final USSDMessageAssembler messageAssembler;
	private final UserManagementService userManager;
	private final CacheUtilService cacheManager;

	public UssdGeoApiServiceImpl(UssdSupport ussdSupport, LocationInfoBroker locationInfoBroker, AsyncUserLogger userLogger, USSDMessageAssembler messageAssembler, UserManagementService userManager, CacheUtilService cacheManager) {
		this.ussdSupport = ussdSupport;
		this.locationInfoBroker = locationInfoBroker;
		this.userLogger = userLogger;
		this.messageAssembler = messageAssembler;
		this.userManager = userManager;
		this.cacheManager = cacheManager;
	}

	@Override
	public USSDMenu openingMenu(UserMinimalProjection user, String dataSetLabel) {
		long startTime = System.currentTimeMillis();
		USSDMenu menu;
		List<Locale> availableLocales = locationInfoBroker.getAvailableLocalesForDataSet(dataSetLabel);
		log.info("checking if need language for geo api, user language code = ", user.getLanguageCode());
		if (!StringUtils.isEmpty(user.getLanguageCode()) && !availableLocales.contains(new Locale(user.getLanguageCode()))) {
			menu = infoSetMenu(dataSetLabel, user, true);
		} else {
			menu = languageMenu(dataSetLabel, "/infoset?dataSet=" + dataSetLabel + "&language=", availableLocales, user);
		}
		log.info("GeoAPI opening menu took {} msecs, now recording use", System.currentTimeMillis() - startTime);
		userLogger.recordUserLog(user.getUid(), UserLogType.GEO_APIS_CALLED, dataSetLabel, UserInterfaceType.USSD);
		return menu;
	}

	@Override
	@Transactional
	public Request processOpeningMenu(String dataSet, String inputNumber, Boolean forceOpening) throws URISyntaxException {
		User user = userManager.loadOrCreateUser(inputNumber, UserInterfaceType.USSD);
		boolean possiblyInterrupted = forceOpening == null || !forceOpening;
		if (possiblyInterrupted && cacheManager.fetchUssdMenuForUser(inputNumber) != null) {
			String returnUrl = cacheManager.fetchUssdMenuForUser(inputNumber);
			USSDMenu promptMenu = new USSDMenu(ussdSupport.getMessage("home.start.prompt-interrupted", user));
			promptMenu.addMenuOption(returnUrl, ussdSupport.getMessage("home.start.interrupted.resume", user));
			promptMenu.addMenuOption("geo/opening/" + dataSet + "?forceOpening=true", ussdSupport.getMessage("home.start.interrupted.start", user));
			return ussdSupport.menuBuilder(promptMenu);
		} else {
			return ussdSupport.menuBuilder(openingMenu(ussdSupport.convert(user), dataSet));
		}
	}

	private USSDMenu languageMenu(final String dataSetLabel,
								  final String subsequentUrl,
								  final List<Locale> availableLocales,
								  final UserMinimalProjection user) {
		USSDMenu menu = new USSDMenu(messageAssembler.getMessage("language.prompt." + dataSetLabel, user));
		availableLocales.forEach(l -> menu.addMenuOption(REL_PATH + subsequentUrl + l.toString(),
				messageAssembler.getMessage("language." + l.toString(), user)));
		return menu;
	}

	@Override
	@Transactional
	public Request processChooseInfoSet(String inputNumber, String dataSet, Locale language, Boolean interrupted) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalAndStashMenu(inputNumber, saveUrl("/infoset", dataSet));
		log.info("locale: {}, interrupted: {}", language, interrupted);
		if (interrupted == null || !interrupted) {
			user = userManager.updateUserLanguage(user.getUid(), language, UserInterfaceType.USSD);
			log.info("set user language to: ", user.getLanguageCode());
		}
		return ussdSupport.menuBuilder(infoSetMenu(dataSet, user, false));
	}

	private USSDMenu infoSetMenu(final String dataSet, final UserMinimalProjection user, final boolean skippedLanguage) {
		USSDMenu menu = new USSDMenu(messageAssembler.getMessage("service.prompt." + dataSet
				+ (skippedLanguage ? ".open" : ""), user));
		Map<String, String> infoSets = locationInfoBroker.getAvailableInfoAndLowestLevelForDataSet(dataSet);
		log.info("info sets retrieved: {}", infoSets);

		infoSets.forEach((key, value) -> {
			log.info("adding option, key: {}, value: {}", key, value);
			String msg = messageAssembler.getMessage(dataSet + ".service.options." + key, user);
			String url = REL_PATH + "/location/" + value.toLowerCase() + "/" + dataSet + "/" + key;
			log.info("adding msg: {}, url: {}", msg, url);
			menu.addMenuOption(url, msg);
			log.info("menu options: {}", menu.getMenuOptions());
		});
		return menu;
	}

	private String saveUrl(String menu, String dataSet) {
		return StringUtils.isEmpty(dataSet) ? REL_PATH + menu + "?interrupted=1" :
				REL_PATH + menu + "?dataSet=" + dataSet + "&interrupted=1";
	}

	@Override
	public Request processChooseProvinceMenu(String inputNumber, String dataSet, String infoSet, Boolean interrupted) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalAndStashMenu(inputNumber, saveUrl("/location/place/" + dataSet + "/" + infoSet, null));
		log.info("infoset: {}, interrupted: {}", infoSet, interrupted);
		return ussdSupport.menuBuilder(provinceMenu(dataSet, infoSet, user));
	}

	private USSDMenu provinceMenu(final String dataSetLabel, final String infoSet, final UserMinimalProjection user) {
		USSDMenu menu = new USSDMenu(messageAssembler.getMessage("province.prompt." + dataSetLabel, user));
		List<Province> provinces = locationInfoBroker.getAvailableProvincesForDataSet(dataSetLabel);
		final String baseUrl = REL_PATH + "/info/send/province/" + dataSetLabel + "/" + infoSet + "?province=";
		provinces.forEach(p -> menu.addMenuOption(baseUrl + p.name(),
				messageAssembler.getMessage("province." + p.name().substring("ZA_".length()), user)));
		return menu;
	}

	@Override
	public Request processEnterTownMenu(String inputNumber, String dataSet, String infoSet) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalAndStashMenu(inputNumber, saveUrl("/location/place/" + dataSet + "/" + infoSet, null));
		final String prompt = "To send you the closest clinics, please enter the nearest post code, or type a town name:";
		return ussdSupport.menuBuilder(new USSDMenu(prompt, "geo/location/place/select/" + dataSet + "/" + infoSet));
	}

	@Override
	@Transactional
	public Request processSelectTownAndSendMenu(String inputNumber, String dataSet, String infoSet, String userInput) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalAndStashMenu(inputNumber, saveUrl("/location/place/select/" + dataSet + "/" + infoSet, null));
		List<TownLookupResult> results = locationInfoBroker.lookupPostCodeOrTown(userInput.trim(), null);
		if (results.isEmpty()) {
			final String prompt = ussdSupport.getMessage("user.town.none.prompt", user);
			final String currentUrl = "geo/location/place/select/" + dataSet + "/" + infoSet;
			return ussdSupport.menuBuilder(new USSDMenu(prompt, currentUrl));
		} else if (results.size() == 1) {
			return processSendInfoForPlace(inputNumber, dataSet, infoSet, results.get(0).getPlaceId());
		} else {
			final String prompt = ussdSupport.getMessage("user.town.many.prompt", user);
			final USSDMenu menu = new USSDMenu(prompt);
			final String baseUrl = "geo/info/send/place/" + dataSet + "/" + infoSet + "?placeId=";
			menu.addMenuOptions(results.stream().collect(Collectors.toMap(
					lookup -> baseUrl + USSDUrlUtil.encodeParameter(lookup.getPlaceId()),
					TownLookupResult::getDescription)));
			return ussdSupport.menuBuilder(menu);
		}
	}

	@Override
	@Transactional
	public Request processSendInfoForPlace(String inputNumber, String dataSet, String infoSet, String placeId) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalAndStashMenu(inputNumber, null);
		locationInfoBroker.assembleAndSendForPlace(dataSet, infoSet, placeId, user.getUid());
		final String prompt = messageAssembler.getMessage(dataSet + ".sent.prompt", new String[]{"5"}, user);
		return ussdSupport.menuBuilder(new USSDMenu(prompt));
	}

	@Override
	@Transactional
	public Request processSendInfoForProvince(String inputNumber, String dataSet, String infoSet, Province province) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalAndStashMenu(inputNumber, null);
		return ussdSupport.menuBuilder(sendMessageWithInfo(dataSet, infoSet, province, user));
	}

	private USSDMenu sendMessageWithInfo(String dataSet, String infoTag, Province province, UserMinimalProjection user) {
		List<String> records = locationInfoBroker.retrieveRecordsForProvince(dataSet, infoTag, province, user.getLocale());
		final String prompt = messageAssembler.getMessage(dataSet + ".sent.prompt",
				new String[]{String.valueOf(records.size())}, user);
		locationInfoBroker.assembleAndSendRecordMessage(dataSet, infoTag, province, user.getUid());
		return new USSDMenu(prompt); // todo : include option to send safety alert if they are on? (and v/versa)
	}
}
