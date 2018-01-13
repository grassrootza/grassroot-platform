package za.org.grassroot.webapp.controller.ussd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.integration.location.LocationInfoBroker;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController @Slf4j
@RequestMapping(value = "/ussd/geo", method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@ConditionalOnProperty(name = "grassroot.geo.apis.enabled", matchIfMissing = false)
public class USSDGeoApiController extends USSDBaseController {

    private static final String REL_PATH = "/geo";

    private final LocationInfoBroker locationInfoBroker;
    private final USSDMessageAssembler messageAssembler;

    @Autowired
    public USSDGeoApiController(LocationInfoBroker locationInfoBroker, USSDMessageAssembler messageAssembler) {
        this.locationInfoBroker = locationInfoBroker;
        this.messageAssembler = messageAssembler;
    }

    private USSDMenu provinceMenu(final String dataSetLabel,
                                  final String subsequentUrl,
                                  boolean skippedLanguage,
                                  final User user) {
        USSDMenu menu = new USSDMenu(messageAssembler.getMessage("province.prompt." + dataSetLabel
                + (skippedLanguage ? ".open" : ""), user));
        List<Province> provinces = locationInfoBroker.getAvailableProvincesForDataSet(dataSetLabel);
        provinces.forEach(p -> menu.addMenuOption(REL_PATH + subsequentUrl + p.name(),
                messageAssembler.getMessage("province." + p.name(), user)));
        return menu;
    }

    private USSDMenu languageMenu(final String dataSetLabel,
                                  final String subsequentUrl,
                                  final List<Locale> availableLocales,
                                  final User user) {
        USSDMenu menu = new USSDMenu(messageAssembler.getMessage("language.prompt." + dataSetLabel, user));
        availableLocales.forEach(l -> menu.addMenuOption(REL_PATH + subsequentUrl + l.toString(),
                messageAssembler.getMessage("language." + l.toString(), user)));
        return menu;
    }

    USSDMenu openingMenu(final User user, final String dataSetLabel) {
        long startTime = System.currentTimeMillis();
        USSDMenu menu;
        List<Locale> availableLocales = locationInfoBroker.getAvailableLocalesForDataSet(dataSetLabel);
        log.info("checking if need language for geo api, user language code = ", user.getLanguageCode());
        if (!StringUtils.isEmpty(user.getLanguageCode()) && !availableLocales.contains(new Locale(user.getLanguageCode()))) {
            menu = provinceMenu(dataSetLabel, "/info/select?dataSet=" + dataSetLabel + "&province=", true, user);
        } else {
            menu = languageMenu(dataSetLabel, "/province?dataSet=" + dataSetLabel + "&language=", availableLocales, user);
        }
        log.info("GeoAPI opening menu took {} msecs", System.currentTimeMillis() - startTime);
        return menu;
    }

    @RequestMapping(value = "/province")
    public Request chooseProvince(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam String dataSet,
                                  @RequestParam(required = false) Locale language,
                                  @RequestParam(required = false) Boolean interrupted) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveUrl("/province", dataSet));
        if (interrupted != null && !interrupted) {
            userManager.updateUserLanguage(user.getUid(), language);
            user.setLanguageCode(language.getLanguage()); // to avoid a reload (even if H caches)
        }
        return menuBuilder(provinceMenu(dataSet, "/info/select?dataSet=" + dataSet + "&province=", false, user));
    }

    @RequestMapping(value = "/info/select")
    public Request chooseInfo(@RequestParam(value = phoneNumber) String inputNumber,
                              @RequestParam String dataSet,
                              @RequestParam Province province) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveUrl("/info/select", dataSet) + "&province=" + province);
        List<String> availableInfo = locationInfoBroker.getAvailableInfoForProvince(dataSet, province, user.getLocale());
        if (availableInfo.size() == 1) {
            return menuBuilder(sendMessageWithInfo(dataSet, availableInfo.get(0), province, user));
        } else {
            USSDMenu menu = new USSDMenu("What info?");
            final String urlRoot = REL_PATH + "/info/send?dataSet=" + dataSet + "&province=" + province + "&infoTag=";
            availableInfo.forEach(al -> {
                menu.addMenuOption(urlRoot + al, al); // todo : think through option message (and info set extraction in general)
            });
            return menuBuilder(menu);
        }
    }

    @RequestMapping(value = "/info/send")
    public Request sendInfo(@RequestParam(value = phoneNumber) String inputNumber,
                            @RequestParam String dataSet,
                            @RequestParam String infoTag,
                            @RequestParam Province province) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, null);
        return menuBuilder(sendMessageWithInfo(dataSet, infoTag, province, user));
    }

    private USSDMenu sendMessageWithInfo(String dataSet, String infoTag, Province province, User user) {
        List<String> records = locationInfoBroker.retrieveRecordsForProvince(dataSet, infoTag, province, user.getLocale());
        // todo : particle filter etc to decide on a likely closest record, and then do the rest
        final String prompt = messageAssembler.getMessage(dataSet + ".sent.prompt",
                new String[] { String.valueOf(records.size()) }, user);
        locationInfoBroker.assembleAndSendRecordMessage(dataSet, infoTag, province, user.getUid());
        return new USSDMenu(prompt); // todo : include option to send safety alert if they are on? (and v/versa)
    }

    private String saveUrl(String menu, String dataSet) {
        return REL_PATH + menu + "?dataSet=" + dataSet + "&interrupted=1";
    }

}
