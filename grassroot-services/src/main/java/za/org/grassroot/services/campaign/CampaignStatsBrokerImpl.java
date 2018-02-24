package za.org.grassroot.services.campaign;

import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.CampaignLogRepository;
import za.org.grassroot.core.repository.CampaignRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.annotation.Nullable;
import javax.persistence.criteria.Join;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.DAYS;

@Service @Slf4j
public class CampaignStatsBrokerImpl implements CampaignStatsBroker {

    private static final DateTimeFormatter STATS_DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter STATS_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final String GROWTH_CACHE = "campaign_growth_stats";
    private static final String CURRENT_CACHE = "campaign_current_stats";

    // for convenience
    private static final ZoneId ZONE = Clock.systemDefaultZone().getZone();

    private final CampaignRepository campaignRepository;
    private final CampaignLogRepository campaignLogRepository;
    private final UserRepository userRepository;

    private final CacheManager cacheManager;

    // NB: sequence here must be in order of importance, i.e., later in sequence -> later in user journey
    private static final List<CampaignLogType> ENGAGEMENT_LOG_TYPES = Arrays.asList(
            CampaignLogType.CAMPAIGN_FOUND,
            CampaignLogType.CAMPAIGN_EXITED_NEG,
            CampaignLogType.CAMPAIGN_PETITION_SIGNED,
            CampaignLogType.CAMPAIGN_USER_ADDED_TO_MASTER_GROUP,
            CampaignLogType.CAMPAIGN_SHARED
    );

    private static final Specification<CampaignLog> isEngagementLog = (root, query, cb) -> root.get("campaignLogType").in(ENGAGEMENT_LOG_TYPES);

    private static Specification<CampaignLog> forCampaign(Campaign campaign) {
        return (root, query, cb) -> cb.equal(root.get("campaign"), campaign);
    }

    private static Specifications<CampaignLog> engagementLogsForCampaign(Campaign campaign) {
        return Specifications.where(forCampaign(campaign)).and(isEngagementLog);
    }

    @Autowired
    public CampaignStatsBrokerImpl(CampaignRepository campaignRepository, CampaignLogRepository campaignLogRepository, UserRepository userRepository, CacheManager cacheManager) {
        this.campaignRepository = campaignRepository;
        this.campaignLogRepository = campaignLogRepository;
        this.userRepository = userRepository;
        this.cacheManager = cacheManager;
    }

    @Override
    public void clearCampaignStatsCache(String campaignUid) {
        Cache staticCache = getStatsCache(CURRENT_CACHE);
        staticCache.remove(campaignUid + "_engagement");
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> getCampaignMembershipStats(String campaignUid, Integer year, @Nullable Integer month) {
        Cache cache = getStatsCache(GROWTH_CACHE);

        final String cacheKey = campaignUid + "-" + year + "-" + month;
        if (cache.isKeyInCache(cacheKey)) {
            return (Map<String, Integer>) cache.get(cacheKey).getObjectValue();
        }

        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        LocalDate startTime = DateTimeUtil.getStartTimeForEntityStats(year, month, campaign.getCreatedDateTime());
        LocalDate endTime = DateTimeUtil.getEndTime(year, month, startTime);

        Instant startTimeInstant = startTime.atStartOfDay(Clock.systemDefaultZone().getZone()).toInstant();
        Instant endTimeInstant = endTime.atStartOfDay(Clock.systemDefaultZone().getZone()).toInstant();

        Specification<CampaignLog> memberAdded = (root, query, cb) -> cb.equal(root.get("campaignLogType"),
                CampaignLogType.CAMPAIGN_USER_ADDED_TO_MASTER_GROUP);
        Specification<CampaignLog> withinTime = (root, query, cb) -> cb.between(root.get("creationTime"),
                startTimeInstant, endTimeInstant);
        List<CampaignLog> matchingLogs = campaignLogRepository.findAll(Specifications
                .where(forCampaign(campaign)).and(memberAdded).and(withinTime));

        // strip out duplicates, so we have only the first entry
        Map<Long, CampaignLog> lastStageMap = new LinkedHashMap<>();
        matchingLogs.stream().filter(log -> !lastStageMap.containsKey(log.getUser().getId()))
                .forEach(log -> lastStageMap.put(log.getUser().getId(), log));
        log.info("latest stage map: {}", lastStageMap);

        int currentMemberCount = 0; // replace with count of group at start

        List<CampaignLog> distinctLogs = new ArrayList<>(lastStageMap.values());
        Map<String, Integer> results = new LinkedHashMap<>();

        if (year != null && month != null) {
            // do it by day
            Map<String, List<CampaignLog>> changes = distinctLogs .stream().collect(Collectors.groupingBy(
                    cl -> STATS_DAY_FORMAT.format(cl.getCreationTime().atZone(Clock.systemDefaultZone().getZone()))));
            LocalDate currDay = startTime;
            while (currDay.isBefore(endTime)) {
                String dayKey = STATS_DAY_FORMAT.format(currDay);
                List<CampaignLog> dayChanges = changes.get(dayKey);
                if (dayChanges != null) {
                    currentMemberCount += dayChanges.size();
                }
                results.put(dayKey, currentMemberCount);
                currDay = currDay.plusDays(1);
            }
        } else {
            // do it by months
            Map<String, List<CampaignLog>> changes = distinctLogs .stream().collect(Collectors.groupingBy(
                    cl -> STATS_MONTH_FORMAT.format(cl.getCreationTime().atZone(Clock.systemDefaultZone().getZone()))));
            LocalDate currMonth = startTime;
            while (currMonth.getYear() < endTime.getYear() || currMonth.getMonthValue() < endTime.getMonthValue()) {
                String monthKey = STATS_MONTH_FORMAT.format(currMonth);
                List<CampaignLog> monthChanges = changes.get(monthKey);
                if (monthChanges != null) {
                    currentMemberCount += monthChanges.size();
                }
                results.put(monthKey, currentMemberCount);
                currMonth = currMonth.plusMonths(1);
            }
        }

        cache.put(new Element(cacheKey, results));
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getCampaignConversionStats(String campaignUid) {
        Cache cache = getStatsCache(CURRENT_CACHE);
        final String cacheKey = campaignUid + "_engagement";
        if (cache.isKeyInCache(cacheKey))
            return (Map<String, Long>) cache.get(cacheKey).getObjectValue();

        Campaign campaign = campaignRepository.findOneByUid(campaignUid);

        // step 1: get all engagement logs and sort them in ascending order of degree of engagement
        List<CampaignLog> allEngagementLogs = campaignLogRepository.findAll(engagementLogsForCampaign(campaign));
        allEngagementLogs.sort(Comparator.comparingInt(log -> ENGAGEMENT_LOG_TYPES.indexOf(log.getCampaignLogType())));

        // step 2: divide into a map, for each user, of their latest engagement status
        Map<Long, CampaignLogType> lastStageMap = new LinkedHashMap<>();
        allEngagementLogs.forEach(log -> lastStageMap.put(log.getUser().getId(), log.getCampaignLogType()));
        log.info("latest stage map: {}", lastStageMap);

        // step 3: count the number of users in a particular stage
        Map<String, Long> result = lastStageMap.entrySet().stream()
                .collect(Collectors.groupingBy(entry -> entry.getValue().toString(), Collectors.counting()));
        log.info("okay and the collected map = {}", result);

        cache.put(new Element(cacheKey, result));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getCampaignChannelStats(String campaignUid) {
        Cache cache = getStatsCache(CURRENT_CACHE);
        final String cacheKey = campaignUid + "_channels";
        if (cache.isKeyInCache(cacheKey))
            return (Map<String, Long>) cache.get(cacheKey).getObjectValue();

        // step 1: get all engagement logs
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        List<CampaignLog> engagementLogs = campaignLogRepository.findAll(engagementLogsForCampaign(campaign));

        // step 2: strip out duplicate user-interface pairs (uses a slight redundancy but not seeing a more elegant way right now)
        Map<String, UserInterfaceType> channelMap = new LinkedHashMap<>();
        engagementLogs.stream()
                .filter(log -> log.getChannel() != null)
                .forEach(log -> {
            final String key = log.getUser().getId() + "_" + log.getChannel();
            channelMap.put(key, log.getChannel());
        });

        // step 3: count the number of interfaces
        log.info("okay, channel map now looks like: {}", channelMap);
        Map<String, Long> result = channelMap.entrySet().stream()
                .collect(Collectors.groupingBy(entry -> entry.getValue().toString(), Collectors.counting()));

        cache.put(new Element(cacheKey, result));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getCampaignProvinceStats(String campaignUid) {
        Cache cache = getStatsCache(CURRENT_CACHE);
        final String cacheKey = campaignUid + "_provinces";
        if (cache.isKeyInCache(cacheKey))
            return (Map<String, Long>) cache.get(cacheKey).getObjectValue();

        // step 1: get all users that have engaged
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        Specification<User> engagedUsers = (root, query, cb) -> {
            Join<User, CampaignLog> campaignLogs = root.join("campaignLogs");
            query.distinct(true);
            return cb.and(cb.equal(campaignLogs.get("campaign"), campaign),
                    campaignLogs.get("campaignLogType").in(ENGAGEMENT_LOG_TYPES));
        };

        List<User> users = userRepository.findAll(engagedUsers);
        Map<String, Long> result = users.stream().collect(
                Collectors.groupingBy(user -> user.getProvince() == null ?
                        "UNKNOWN" : user.getProvince().name(), Collectors.counting()));

        cache.put(new Element(cacheKey, result));

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getCampaignActivityCounts(String campaignUid, CampaignActivityStatsRequest request) {
        Cache cache = getStatsCache(CURRENT_CACHE);
        final String cacheKey = campaignUid + "_activity_" + request.getDatasetDivision() + "_" + request.getTimePeriod();
        log.info("activity stats, cache key: {}", cacheKey);
        if (cache.isKeyInCache(cacheKey))
            return (Map<String, Object>) cache.get(cacheKey).getObjectValue();

        // step 1 : get engagement logs that need dividing up, depending on time period
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);

        Instant startTime = request.getStartTime(campaign.getCreatedDateTime());
        Instant endTime = request.getEndTime(campaign.getCreatedDateTime());

        Specification<CampaignLog> withinPeriod = (root, query, cb) -> cb.between(root.get("creationTime"), startTime, endTime);
        List<CampaignLog> allEngagementLogs = campaignLogRepository.findAll(engagementLogsForCampaign(campaign).and(withinPeriod),
                new Sort(Sort.Direction.ASC, "creationTime"));

        // step 2: as above, filter out user's duplicates in funnel
        Map<Long, CampaignLog> lastStageMap = new LinkedHashMap<>();
        allEngagementLogs.forEach(log -> lastStageMap.put(log.getUser().getId(), log));

        // step 3: create a formatter for the time, for grouping the entities, depending on duration
        long daysBetween = DAYS.between(startTime, endTime);
        log.info("start time: {}, end time: {}, days between: {}", startTime, endTime, daysBetween);
        DateTimeFormatter groupingFormatter = daysBetween > 31 ? DateTimeFormatter.ofPattern("MMM") :
                daysBetween > 7 ? DateTimeFormatter.ofPattern("W") :  DateTimeFormatter.ofPattern("E");

        List<String> timeKeys = Stream.iterate(LocalDate.from(startTime.atZone(ZONE)), date -> date.plusDays(1)).limit(daysBetween)
                .map(groupingFormatter::format).distinct().collect(Collectors.toList());
        log.info("alright, time keys = {}", timeKeys);

        // step 4: divide up the data, into channels or provinces, grouped by the set above
        Map<String, Object> results = new HashMap<>();
        results.put("TIME_UNITS", timeKeys);

        List<CampaignLog> logs = new ArrayList<>(lastStageMap.values());
        if (request.isByChannel()) {
            Set<UserInterfaceType> channels = logs.stream().map(CampaignLog::getChannel).filter(Objects::nonNull).collect(Collectors.toSet());
            channels.forEach(channel -> {
                // step 5: group logs by formatted date string (note: may be a clever way to do this just in the stream,
                // though preserving ordering is useful (more so than a little additional elegance in code)
                results.put(channel.toString(), groupLogsByTime(logs, log -> channel.equals(log.getChannel()), groupingFormatter, timeKeys));
            });
        } else {
            List<User> users = userRepository.findAll(lastStageMap.keySet());
            List<Province> provinces = users.stream().map(User::getProvince).filter(Objects::nonNull).collect(Collectors.toList());
            provinces.forEach(province -> {
                // these user entities should be Hibernate-cached by this stage, but watch this query in any case
                results.put(province.toString(), groupLogsByTime(logs, log -> province.equals(log.getUser().getProvince()), groupingFormatter, timeKeys));
            });
            results.put("UNKNOWN", groupLogsByTime(logs, log -> log.getUser().getProvince() == null, groupingFormatter, timeKeys));
        }

        cache.put(new Element(cacheKey, results));
        return results;
    }

    private Map<String, Integer> groupLogsByTime(List<CampaignLog> logs, Predicate<CampaignLog> filter, DateTimeFormatter groupingFormatter, List<String> timeKeys) {
        Map<String, Integer> timeUnitsCount = new LinkedHashMap<>();
        Map<String, List<CampaignLog>> groupedLogs = logs.stream()
                .filter(filter).collect(Collectors.groupingBy(log -> groupingFormatter.format(log.getCreationTime().atZone(ZONE))));
        timeKeys.forEach(timeKey -> timeUnitsCount.put(timeKey, groupedLogs.containsKey(timeKey) ? groupedLogs.get(timeKey).size() : 0));
        return timeUnitsCount;
    }

    private Cache getStatsCache(String cacheName) {
        if (!cacheManager.cacheExists(cacheName)) {
            // provides for, in effect, 500 campaigns being accessed almost simultaneously
            Cache statsCache = new Cache(new CacheConfiguration(cacheName, 2000).timeToIdleSeconds(900).eternal(false));
            cacheManager.addCacheIfAbsent(statsCache);
        }
        return cacheManager.getCache(cacheName);
    }

}
