package za.org.grassroot.integration.location;

import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.domain.ConfigVariable;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.Address;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.UserLocationLog;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.repository.AddressRepository;
import za.org.grassroot.core.repository.ConfigRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserLocationLogRepository;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component @Slf4j
@ConditionalOnProperty("grassroot.municipal.filtering.enabled")
public class MunicipalFilteringBrokerImpl implements MunicipalFilteringBroker {

    private RestTemplate restTemplate;

    private static final int NUMBER_DAYS_CUT_OFF = 7;
    private static final ParameterizedTypeReference<Map<String,Municipality>> MUNICIPALITY_RESPONSE_TYPE =
            new ParameterizedTypeReference<Map<String, Municipality>>() {};

    private final UserLocationLogRepository userLocationLogRepository;
    private final CacheManager cacheManager;
    private final GroupRepository groupRepository;
    private final ConfigRepository configRepository;
    private final AddressRepository addressRepository;

    @Autowired
    public MunicipalFilteringBrokerImpl(UserLocationLogRepository userLocationLogRepository, CacheManager cacheManager, GroupRepository groupRepository, ConfigRepository configRepository, AddressRepository addressRepository) {
        this.userLocationLogRepository = userLocationLogRepository;
        this.cacheManager = cacheManager;
        this.groupRepository = groupRepository;
        this.configRepository = configRepository;
        this.addressRepository = addressRepository;
    }

    @Autowired
    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public List<Municipality> getMunicipalitiesForProvince(Province province) {
        UriComponentsBuilder componentsBuilder = UriComponentsBuilder.fromHttpUrl("http://mapit.code4sa.org/area/");
        String areaId;

        // Some provinces have Municipality Demarcation Board code and others use integer MapIt area id.
        switch (province) {
            case ZA_GP:     areaId = "MDB:GT/children";     break;//uses Municipality Demarcation Board code
            case ZA_LP:     areaId = "4292/children";       break;//uses MapIt area id
            case ZA_NC:     areaId = "4295/children";       break;
            case ZA_EC:     areaId = "4288/children";       break;
            case ZA_KZN:    areaId = "4291/children";       break;
            case ZA_FS:     areaId = "4289/children";       break;
            case ZA_MP:     areaId = "4293/children";       break;
            case ZA_NW:     areaId = "MDB:NW/children";     break;
            case ZA_WC:     areaId = "MDB:WC/children";     break;
            default:        return new ArrayList<>();
        }

        componentsBuilder = componentsBuilder.path(areaId);
        log.debug("Composed URI: {}", componentsBuilder.toUriString());

        ResponseEntity<Map<String, Municipality>> result = restTemplate.exchange(componentsBuilder.build().toUri(), HttpMethod.GET, null, MUNICIPALITY_RESPONSE_TYPE);

        log.info("Received response: {}", result);

        List<Municipality> municipalities = new ArrayList<>();
        if (result.getBody() != null) {
            municipalities = new ArrayList<>(result.getBody().values());
        }

        log.info("Processed munis: {}", municipalities);
        return municipalities;
    }

    //    Loading users with location not null
    @Override
    public  void fetchMunicipalitiesForUsersWithLocations(Integer batchSize) {
        Instant dayDataCutOff = Instant.now().minus(NUMBER_DAYS_CUT_OFF, ChronoUnit.DAYS);
        PageRequest pageable = PageRequest.of(0, 100, Sort.Direction.ASC, "timestamp");
        Page<UserLocationLog> userLocationLogs = userLocationLogRepository.findByMunicipalityIdIsNullAndTimestampAfter(dayDataCutOff, pageable);
        log.info("About to retrieve municipalities for {} logs, out of {} possible total", userLocationLogs.getNumberOfElements(), userLocationLogs.getTotalElements());

        userLocationLogs.forEach(log -> {
            Municipality muni = fetchMunicipalityByCoordinates(log.getUserUid(), log.getLocation());
            log.setLocationData(null, String.valueOf(muni.getId()), muni.getName());
        });

        userLocationLogRepository.saveAll(userLocationLogs);
    }

    private Municipality fetchMunicipalityByCoordinates(String userUid, GeoLocation location) {
        UriComponentsBuilder componentsBuilder = UriComponentsBuilder.fromHttpUrl("http://mapit.code4sa.org/point/4326/");
        String latLong = location.getLongitude() + "," + location.getLatitude();

        componentsBuilder = componentsBuilder.path(latLong).queryParam("type","MN");

        log.debug("Composed URI: {}", componentsBuilder.toUriString());

        ResponseEntity<Map<String,Municipality>> result = restTemplate.exchange(componentsBuilder.build().toUri(),HttpMethod.GET,null, MUNICIPALITY_RESPONSE_TYPE);

        Municipality municipality = null;
        final boolean hasResult = result.getBody() != null && !result.getBody().isEmpty();
        if (hasResult) {
            municipality = result.getBody().entrySet().iterator().next().getValue();
            cacheMunicipality(userUid, municipality);
            log.info("Found a municipality! = {}",municipality);
        } else {
            log.info("No municipality found! Uri: {}", componentsBuilder.build().toUri());
        }

        return municipality;
    }

    private void cacheMunicipality(String userUid, Municipality municipality) {
        Cache cache = cacheManager.getCache("user_municipality");
        cache.put(new Element(userUid,municipality));
    }

    @Override
    public UserMunicipalitiesResponse getMunicipalitiesForUsersWithLocationFromCache(Set<String> userUids) {
        Cache cache = cacheManager.getCache("user_municipality");
        Map<String, Municipality> municipalityMap = new HashMap<>();

        userUids.stream().filter(cache::isKeyInCache).forEach(uid -> {
            Element cacheElement = cache.get(uid);
            if (cacheElement != null && cacheElement.getObjectValue() != null) {
                Municipality municipality = (Municipality) cacheElement.getObjectValue();
                municipalityMap.put(uid, municipality);
            }
        });

        List<String> notYetCachedUids = new ArrayList<>(userUids);
        notYetCachedUids.removeAll(municipalityMap.keySet());
        // for these, we go to the DB
        notYetCachedUids.forEach(uid -> {
            UserLocationLog log = userLocationLogRepository.findFirstByUserUidAndMunicipalityIdIsNotNullOrderByTimestampDesc(uid);
            if (log != null) {
                Municipality municipality = new Municipality(log.getMunicipalityName(), Integer.parseInt(log.getMunicipalityId()), null);
                municipalityMap.put(uid, municipality);
                cacheMunicipality(uid, municipality);
            }
        });

        List<String> noMunicipalityUids = new ArrayList<>(notYetCachedUids);
        noMunicipalityUids.removeAll(municipalityMap.keySet());

        log.info("Municipalities for users with location from cache is = {}",municipalityMap);
        return new UserMunicipalitiesResponse(municipalityMap, noMunicipalityUids);
    }

    @Override
    public long countUserLocationLogs(boolean countAll, boolean includeNoMunicipality) {
        Optional<ConfigVariable> configVariable = configRepository.findOneByKey("days.location.log.check");
        int userLocationLogsPeriod = configVariable.map(var -> Integer.parseInt(var.getValue())).orElse(0);
        Instant threshold = countAll ? DateTimeUtil.getEarliestInstant() : Instant.now().minus(userLocationLogsPeriod, ChronoUnit.DAYS);

        Specification<UserLocationLog> timestamp = (root, query, cb) -> cb.greaterThan(root.get("timestamp"), threshold);
        Specification<UserLocationLog> hasMunicipality = (root, query, cb) -> cb.isNotNull(root.get("municipalityId"));

        Specification<UserLocationLog> spec = includeNoMunicipality ? timestamp : timestamp.and(hasMunicipality);

        return userLocationLogRepository.count(spec);
    }

    @Async
    @Override
    public void saveLocationLogsFromAddress(int pageSize) {
        List<Address> addresses = addressRepository.loadAddressesWithLocation();
        if (addresses != null) {
            log.info("Would need to process {} addresses, but limited to page size of {}", addresses.size(), pageSize);
            Set<UserLocationLog> userLocationLogs = addresses
                    .stream()
                    .limit((long) pageSize)
                    .map(address -> new UserLocationLog(Instant.now(), address.getResident().getUid(), address.getLocation(), address.getLocationSource()))
                    .collect(Collectors.toSet());
            userLocationLogRepository.saveAll(userLocationLogs);
        }
    }

    @Override
    public List<Membership> getMembersInMunicipality(String groupUid, String municipalityIDs){

        Cache cache = cacheManager.getCache("user_municipality");
        Group group = groupRepository.findOneByUid(groupUid);

        Set<User> users = group.getMembers();

        // todo : clean this up properly
        List<Membership> memberships = users.stream()
                .filter(user -> cache.isKeyInCache(user.getUid()))
                .filter(user -> {
                    Element element = cache.get(user.getUid());
                    return element != null && element.getObjectValue() != null;
                })
                .map(user -> {
                    Municipality municipality = (Municipality) cache.get(user.getUid()).getObjectValue();
                    if(municipality.getId() == Integer.valueOf(municipalityIDs)) {
                        return user.getGroupMembership(groupUid);
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull).collect(Collectors.toList());

        log.info("List of users in municipality id = {} members = {}",municipalityIDs,memberships);

        return memberships;
    }

}
