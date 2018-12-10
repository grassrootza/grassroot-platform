package za.org.grassroot.integration.location;

import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.enums.Province;

import java.util.List;
import java.util.Set;

public interface MunicipalFilteringBroker {

    List<Municipality> getMunicipalitiesForProvince(Province province);

    void fetchMunicipalitiesForUsersWithLocations(Integer batchSize);

    List<Membership> getMembersInMunicipality(String groupUid, String municipalityIDs);

    UserMunicipalitiesResponse getMunicipalitiesForUsersWithLocationFromCache(Set<String> user);

    //Counting user location logs. if boolean parameter is true, count from grassroot's begining of time, else within config variable value
    long countUserLocationLogs(boolean countAll, boolean includeNoMunicipality);

    void saveLocationLogsFromAddress(int pageSize);

}
