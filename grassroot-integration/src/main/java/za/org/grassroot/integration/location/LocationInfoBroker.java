package za.org.grassroot.integration.location;

import za.org.grassroot.core.enums.Province;

import java.util.List;
import java.util.Locale;

/*
Central point for calling APIs that provide info on a location
 */
public interface LocationInfoBroker {

    List<Province> getAvailableProvincesForDataSet(String dataSetLabel);

    List<Locale> getAvailableLocalesForDataSet(String dataSetLabel);

    List<String> getAvailableInfoForProvince(String dataSetLabel, Province province, Locale locale);

    List<String> retrieveRecordsForProvince(String dataSetLabel, String infoSetTag, Province province, Locale locale);

    // null sponsoring UID will draw it from data set
    void assembleAndSendRecordMessage(String dataSetLabel, String infoSetTag, Province province,
                                      String targetUserUid);

}
