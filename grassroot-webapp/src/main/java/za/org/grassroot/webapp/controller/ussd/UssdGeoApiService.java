package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.core.dto.UserMinimalProjection;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.Locale;

public interface UssdGeoApiService {
	USSDMenu openingMenu(UserMinimalProjection user, String dataSetLabel);

	Request processOpeningMenu(String dataSet, String inputNumber, Boolean forceOpening) throws URISyntaxException;

	Request processChooseInfoSet(String inputNumber, String dataSet, Locale language, Boolean interrupted) throws URISyntaxException;

	Request processChooseProvinceMenu(String inputNumber, String dataSet, String infoSet, Boolean interrupted) throws URISyntaxException;

	Request processEnterTownMenu(String inputNumber, String dataSet, String infoSet) throws URISyntaxException;

	Request processSelectTownAndSendMenu(String inputNumber, String dataSet, String infoSet, String userInput) throws URISyntaxException;

	Request processSendInfoForPlace(String inputNumber, String dataSet, String infoSet, String placeId) throws URISyntaxException;

	Request processSendInfoForProvince(String inputNumber, String dataSet, String infoSet, Province province) throws URISyntaxException;
}
