package za.org.grassroot.integration.location;

import za.org.grassroot.core.enums.Province;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/*
Central point for calling APIs that provide info on a location, including search
 */
public interface LocationInfoBroker {

    List<TownLookupResult> lookupPostCodeOrTown(String postCodeOrTown, Province province);

    TownLookupResult lookupPlaceDetails(String placeId);

    List<Province> getAvailableProvincesForDataSet(String dataSetLabel);

    List<Locale> getAvailableLocalesForDataSet(String dataSetLabel);

    // linked hashmap
    Map<String, String> getAvailableInfoAndLowestLevelForDataSet(String dataSetLabel);

    List<String> retrieveRecordsForProvince(String dataSetLabel, String infoSetTag, Province province, Locale locale);

    // null sponsoring UID will draw it from data set
    void assembleAndSendRecordMessage(String dataSetLabel, String infoSetTag, Province province,
                                      String targetUserUid);

    void assembleAndSendForPlace(String dataSetLabel, String infoSetTag, String placeId, String targetUserUid);

    List<String> getDatasetLabelsForAccount(String accountUid);

}
