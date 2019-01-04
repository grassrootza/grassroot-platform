package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.core.dto.UserMinimalProjection;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.Locale;

public interface UssdGeoApiService {
	USSDMenu openingMenu(UserMinimalProjection user, String dataSetLabel);

	Request chooseInfoSet(String inputNumber, String dataSet, Locale language, Boolean interrupted) throws URISyntaxException;

	Request chooseProvinceMenu(String inputNumber, String dataSet, String infoSet, Boolean interrupted) throws URISyntaxException;

	Request enterTownMenu(String inputNumber, String dataSet, String infoSet) throws URISyntaxException;

	Request selectTownAndSendMenu(String inputNumber, String dataSet, String infoSet, String userInput) throws URISyntaxException;

	Request sendInfoForPlace(String inputNumber, String dataSet, String infoSet, String placeId) throws URISyntaxException;
}
