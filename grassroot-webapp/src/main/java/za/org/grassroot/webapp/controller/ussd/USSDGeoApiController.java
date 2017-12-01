package za.org.grassroot.webapp.controller.ussd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.integration.location.LocationInfoBroker;
import za.org.grassroot.integration.location.ProvinceSA;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController(value=  "/ussd/geo") @Slf4j
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
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
                                  final User user) {
        USSDMenu menu = new USSDMenu(messageAssembler.getMessage("province.prompt." + dataSetLabel, user));
        List<ProvinceSA> provinces = locationInfoBroker.getAvailableProvincesForDataSet(dataSetLabel);
        provinces.forEach(p -> menu.addMenuOption(subsequentUrl + p.name(),
                messageAssembler.getMessage("province." + p.name(), user)));
        return menu;
    }

    private USSDMenu languageMenu(final String dataSetLabel,
                                  final String subsequentUrl,
                                  final List<Locale> availableLocales,
                                  final User user) {
        USSDMenu menu = new USSDMenu(messageAssembler.getMessage("geoapi.language.prompt." + dataSetLabel, user));
        availableLocales.forEach(l -> menu.addMenuOption(subsequentUrl + l.toString(),
                messageAssembler.getMessage("language." + l.toString(), user)));
        return menu;
    }

    USSDMenu openingMenu(final User user, final String dataSetLabel) {
        List<Locale> availableLocales = locationInfoBroker.getAvailableLocalesForDataSet(dataSetLabel);
        if (!StringUtils.isEmpty(user.getLanguageCode()) || !availableLocales.contains(new Locale(user.getLanguageCode()))) {
            return languageMenu(dataSetLabel, "province?language=" + dataSetLabel + "&province=", availableLocales, user);
        } else {
            return provinceMenu(dataSetLabel, "nextUrl?dataSet=" + dataSetLabel + " &province=", user);
        }
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
        return menuBuilder(provinceMenu(dataSet, "info/select?dataSet=" + dataSet, user));
    }

    @RequestMapping(value = "/info/select")
    public Request chooseInfo(@RequestParam(value = phoneNumber) String inputNumber,
                              @RequestParam String dataSet,
                              @RequestParam ProvinceSA province) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveUrl("/info/select", dataSet) + "&province=" + province);
        List<String> availableInfo = locationInfoBroker.getAvailableInfoForProvince(dataSet, province, user.getLocale());
        if (availableInfo.size() == 1) {
            return menuBuilder(sendMessageWithInfo(dataSet, availableInfo.get(0), province, user));
        } else {
            USSDMenu menu = new USSDMenu("What info?");
            final String urlRoot = REL_PATH + "/info/send?dataSet=" + dataSet + "&province=" + province + "&infoTag=";
            availableInfo.forEach(al -> {
                menu.addMenuOption(urlRoot + al, al); // todo : think through option message
            });
            return menuBuilder(menu);
        }
    }

    @RequestMapping(value = "/info/send")
    public Request sendInfo(@RequestParam(value = phoneNumber) String inputNumber,
                            @RequestParam String dataSet,
                            @RequestParam String infoTag,
                            @RequestParam ProvinceSA province) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, null);
        return menuBuilder(sendMessageWithInfo(dataSet, infoTag, province, user));
    }

    private USSDMenu sendMessageWithInfo(String dataSet, String infoTag, ProvinceSA province, User user) {
        List<String> records = locationInfoBroker.retrieveRecordsForProvince(dataSet, infoTag, province, user.getLocale());
        // todo : particle filter etc to decide on a likely closest record, and then do the rest
        final String prompt = messageAssembler.getMessage("geoapi.sent.prompt", records.get(0));
        locationInfoBroker.assembleAndSendRecordMessage(dataSet, infoTag, province, user.getUid());
        return new USSDMenu(prompt); // todo : include option to send safety alert if they are on? (and v/versa)
    }

    private String saveUrl(String menu, String dataSet) {
        return REL_PATH + menu + "?dataSet=" + dataSet + "&interrupted=1";
    }

}
