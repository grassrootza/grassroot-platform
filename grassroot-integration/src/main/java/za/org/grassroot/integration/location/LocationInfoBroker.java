package za.org.grassroot.integration.location;

import java.util.List;
import java.util.Locale;

/*
Central point for calling APIs that provide info on a location
 */
public interface LocationInfoBroker {

    List<String> getAvailableInfoForProvince(String dataSetLabel, ProvinceSA province, Locale locale);

    List<String> retrieveRecordsForProvince(String dataSetLabel, String infoSetTag, ProvinceSA province, Locale locale);

    void assembleAndSendRecordMessage(String dataSetLabel, String infoSetTag, ProvinceSA province, Locale locale,
                                      String sponsoringAccountUid);

}
