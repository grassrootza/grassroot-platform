package za.org.grassroot.services.campaign;

import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.core.repository.CampaignLogRepository;
import za.org.grassroot.core.repository.CampaignRepository;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.annotation.Nullable;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service @Slf4j
public class CampaignStatsBrokerImpl implements CampaignStatsBroker {

    private static final DateTimeFormatter STATS_DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter STATS_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CampaignRepository campaignRepository;
    private final CampaignLogRepository campaignLogRepository;

    private final CacheManager cacheManager;

    @Autowired
    public CampaignStatsBrokerImpl(CampaignRepository campaignRepository, CampaignLogRepository campaignLogRepository, CacheManager cacheManager) {
        this.campaignRepository = campaignRepository;
        this.campaignLogRepository = campaignLogRepository;
        this.cacheManager = cacheManager;
    }

    private static Specification<CampaignLog> forCampaign(Campaign campaign) {
        return (root, query, cb) -> cb.equal(root.get("campaign"), campaign);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> getCampaignMembershipStats(String campaignUid, Integer year, @Nullable Integer month) {
        Cache cache = getStatsCache("campaign_growth_stats");

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

        int currentMemberCount = 0; // replace with count of group at start

        Map<String, Integer> results = new LinkedHashMap<>();

        if (year != null && month != null) {
            // do it by day
            Map<String, List<CampaignLog>> changes = matchingLogs.stream().collect(Collectors.groupingBy(
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
            // do it by monthu
            Map<String, List<CampaignLog>> changes = matchingLogs.stream().collect(Collectors.groupingBy(
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
    public Map<String, Long> getCampaignEngagementStats(String campaignUid) {
        Cache cache = getStatsCache("campaign_stats");
        final String cacheKey = campaignUid + "_" + "engagement";
        if (cache.isKeyInCache(cacheKey))
            return (Map<String, Long>) cache.get(cacheKey).getObjectValue();

        List<CampaignLogType> sequenceOrder = Arrays.asList(
                CampaignLogType.CAMPAIGN_FOUND,
                CampaignLogType.CAMPAIGN_EXITED_NEG,
                CampaignLogType.CAMPAIGN_PETITION_SIGNED,
                CampaignLogType.CAMPAIGN_USER_ADDED_TO_MASTER_GROUP,
                CampaignLogType.CAMPAIGN_SHARED
                );

        Campaign campaign = campaignRepository.findOneByUid(campaignUid);

        // step 1: get all engagement logs and sort them in ascending order of degree of engagement
        Specification<CampaignLog> engagementLogs = (root, query, cb) -> root.get("campaignLogType").in(sequenceOrder);
        List<CampaignLog> allEngagementLogs = campaignLogRepository.findAll(Specifications
                .where(forCampaign(campaign)).and(engagementLogs));
        allEngagementLogs.sort(Comparator.comparingInt(log -> sequenceOrder.indexOf(log.getCampaignLogType())));

        // step 2: divide into a map, for each user, of their latest engagement status
        Map<Long, CampaignLogType> lastStageMap = new LinkedHashMap<>();
        allEngagementLogs.forEach(log -> lastStageMap.put(log.getUser().getId(), log.getCampaignLogType()));
        log.info("latest stage map: {}", lastStageMap);


        // step 3: count the number of users in a particular stage
        Map<String, Long> result = lastStageMap.entrySet().stream()
                .collect(Collectors.groupingBy(entry -> entry.getValue().toString(), Collectors.counting()));
        log.info("okay and the collected map = {}", result);

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getCampaignChannelStats(String campaignUid) {
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        Cache cache = getStatsCache("campaign_stats");

        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getCampaignProvinceStats(String campaignUid) {
        return null;
    }

    private Cache getStatsCache(String cacheName) {
        if (!cacheManager.cacheExists(cacheName)) {
            Cache statsCache = new Cache(new CacheConfiguration(cacheName, 2000).timeToIdleSeconds(900).eternal(false));
            cacheManager.addCacheIfAbsent(statsCache);
        }
        return cacheManager.getCache(cacheName);
    }
}
