package za.org.grassroot.services.group;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.MembershipRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.specifications.GroupLogSpecifications;
import za.org.grassroot.core.specifications.MembershipSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.annotation.Nullable;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;


@Service
public class GroupStatsBrokerImpl implements GroupStatsBroker {

    private final static ZoneId ZONE_OFFSET = Clock.systemDefaultZone().getZone();


    private final GroupLogRepository groupLogRepository;
    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final CacheManager cacheManager;

    private final String ORGANISATION_TAG_PREFIX = "AFFILIATION";

    public GroupStatsBrokerImpl(
            GroupLogRepository groupLogRepository,
            GroupRepository groupRepository,
            MembershipRepository membershipRepository,
            UserRepository userRepository, CacheManager cacheManager) {
        this.groupLogRepository = groupLogRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.cacheManager = cacheManager;
    }

    private Optional<Map<String, Integer>> checkCache(final String cacheName, final String cacheKey) {
        final Cache cache = cacheManager.getCache(cacheName);
        final Element element = cache.get(cacheKey);
        final Map<String, Integer> resultFromCache = element != null ? (Map<String, Integer>) element.getObjectValue() : null;
        return Optional.ofNullable(resultFromCache);
    }

    @Override
    public Map<String, Integer> getMembershipGrowthStats(String groupUid, @Nullable Integer year, @Nullable Integer month) {
        final String cacheName = "group_stats_member_count";
        final String cacheKey = groupUid + "-" + year + "-" + month;
        return checkCache(cacheName, cacheKey).orElse(calculateMembershipGrowthStats(groupUid, year, month, cacheKey));
    }

    private Map<String, Integer> calculateMembershipGrowthStats(final String groupUid, final Integer year, final Integer month, final String cacheKey) {
        final Cache cache = cacheManager.getCache("group_stats_member_count");

        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");

        Group group = groupRepository.findOneByUid(groupUid);

        final LocalDate startDay = DateTimeUtil.getStartTimeForEntityStats(year, month, group.getCreatedDateTime());
        final LocalDate endDay = DateTimeUtil.getEndTime(year, month, startDay);

        final Instant start = startDay.atStartOfDay(ZONE_OFFSET).toInstant();
        final Instant end = endDay.atStartOfDay(ZONE_OFFSET).toInstant();

        Specification<GroupLog> groupLogSpec = GroupLogSpecifications.memberCountChanges(groupUid, start, end);
        List<GroupLog> memberCountChanges = groupLogRepository.findAll(groupLogSpec);

        Specification<Membership> startingMemberCountSpec = Specification.where(
                MembershipSpecifications.forGroup(group))
                .and(MembershipSpecifications.membersJoinedBefore(start));

        int currentMemberCount = (int) membershipRepository.count(startingMemberCountSpec);

        final DateTimeFormatter timeFormatter = year != null && month !=null ? dayFormatter : monthFormatter;
        Map<String, List<GroupLog>> groupedChanges = memberCountChanges.stream()
                .collect(Collectors.groupingBy(gl -> timeFormatter.format(gl.getCreatedDateTime().atZone(ZONE_OFFSET))));

        final Map<String, Integer> membersCountByTimeStep = new LinkedHashMap<>(); //preserve key order
        if (year != null && month != null) {
            LocalDate currDay = startDay;
            while (currDay.isBefore(endDay)) {
                final String dayKey = dayFormatter.format(currDay);
                final List<GroupLog> dayChanges = groupedChanges.get(dayKey);
                currentMemberCount += countAddedRemoved(dayChanges);
                membersCountByTimeStep.put(dayKey, currentMemberCount);
                currDay = currDay.plusDays(1);
            }
        } else {
            LocalDate currMonth = startDay;
            while (currMonth.getYear() < endDay.getYear() || currMonth.getMonthValue() < endDay.getMonthValue()) {
                final String monthKey = monthFormatter.format(currMonth);
                final List<GroupLog> monthChanges = groupedChanges.get(monthKey);
                currentMemberCount += countAddedRemoved(monthChanges);
                membersCountByTimeStep.put(monthKey, currentMemberCount);
                currMonth = currMonth.plusMonths(1);
            }

        }

        cache.put(new Element(cacheKey, membersCountByTimeStep));
        return membersCountByTimeStep;
    }

    private int countAddedRemoved(List<GroupLog> logs) {
        if (logs != null) {
            final int added = countByLogsIn(logs, GroupLogType.targetUserAddedTypes);
            final int removed = countByLogIs(logs, GroupLogType.GROUP_MEMBER_REMOVED);
            return added - removed;
        } else {
            return 0;
        }
    }

    private int countByLogIs(List<GroupLog> logs, GroupLogType type) {
        return (int) logs.stream().filter(filterByLogIs(type)).count();
    }

    private int countByLogsIn(List<GroupLog> logs, Collection<GroupLogType> types) {
        return (int) logs.stream().filter(filterByLogIn(types)).count();
    }

    private Predicate<? super GroupLog> filterByLogIs(GroupLogType type) {
        return gl -> gl.getGroupLogType() == type;
    }

    private Predicate<? super GroupLog> filterByLogIn(Collection<GroupLogType> types) {
        return gl -> types.contains(gl.getGroupLogType());
    }

    private Collector<Object, ?, Integer> integerSum() {
        return Collectors.reducing(0, e -> 1, Integer::sum);
    }

    @Override
    public Map<String, Integer> getProvincesStats(String groupUid) {
        final String cacheName = "group_stats_provinces";
        return checkCache(cacheName, groupUid).orElseGet(() -> {
            Cache cache = cacheManager.getCache("group_stats_provinces");

            // todo : convert this to calling users directly, this is what is killing the backend
            final Group group = groupRepository.findOneByUid(groupUid);
            final List<User> users = userRepository.findByGroupsPartOfAndIdNot(group, 0L);
            Map<String, Integer> data = users.stream()
                    .filter(u -> u.getProvince() != null)
                    .collect(Collectors.groupingBy(u -> u.getProvince().name(), integerSum()));

            int unknownProvinceCount = (int) users.stream().filter(u -> u.getProvince() == null).count();
            if (unknownProvinceCount > 0)
                data.put("UNKNOWN", unknownProvinceCount);

            cache.put(new Element(groupUid, data));
            return data;
        });

    }

    @Override
    public Map<String, Integer> getSourcesStats(String groupUid) {
        final String cacheName = "group_stats_sources";
        return checkCache(cacheName, groupUid)
                .orElse(assembleMemberStats(groupUid, cacheName, m -> m.getJoinMethod() != null, m -> m.getJoinMethod() == null,
                        m -> m.getJoinMethod().name()));
    }

    private Map<String, Integer> assembleMemberStats(String groupUid, String cacheName,
                                               Predicate<? super Membership> includePredicate,
                                               Predicate<? super Membership> unknownPredicate,
                                               Function<? super Membership, String> groupByName) {
        Cache cache = cacheManager.getCache(cacheName);

        List<Membership> memberships = membershipRepository.findAll(MembershipSpecifications.forGroup(groupUid));

        Map<String, Integer> data = memberships.stream()
                .filter(includePredicate)
                .collect(Collectors.groupingBy(groupByName, integerSum()));

        int unknownSourceCount = (int) memberships.stream().filter(unknownPredicate).count();
        if (unknownSourceCount > 0)
            data.put("UNKNOWN", unknownSourceCount);

        cache.put(new Element(groupUid, data));
        return data;
    }


    @Override
    public Map<String, Integer> getOrganisationsStats(String groupUid) {
        final String cacheName = "group_stats_organisations";
        return checkCache(cacheName, groupUid).orElseGet(() -> {
            final Cache cache = cacheManager.getCache(cacheName);

            List<Membership> memberships = membershipRepository.findAll(MembershipSpecifications.forGroup(groupUid));
            Map<String, Integer> data = memberships.stream()
                    .filter(m -> !m.getAffiliations().isEmpty())
                    .flatMap(m -> m.getAffiliations().stream())
                    .collect(Collectors.groupingBy(s -> s, integerSum()));
            data.put("Unknown", memberships.size());

            cache.put(new Element(groupUid, data));

            return data;
        });


    }


    @Override
    public Map<String, Integer> getMemberDetailsStats(String groupUid) {

        Cache cache = cacheManager.getCache("group_stats_member-details");
        String cacheKey = groupUid;
        Element element = cache.get(cacheKey);
        Map<String, Integer> resultFromCache = element != null ? (Map<String, Integer>) element.getObjectValue() : null;

        if (resultFromCache != null)
            return resultFromCache;

        final Group group = groupRepository.findOneByUid(groupUid);
        final List<User> users = userRepository.findByGroupsPartOfAndIdNot(group, 0L);
        final int groupSize = membershipRepository.countByGroup(group);

        double hasEmail = 0;
        double hasPhoneNumber = 0;
        double hasProvince = 0;
        double hasOrganisation = 0;

        for (User user : users) {
            hasEmail += user.hasEmailAddress() ? 1 : 0;
            hasPhoneNumber += user.hasPhoneNumber() ? 1 : 0;
            hasProvince += user.getProvince() != null ? 1 : 0;
//            hasOrganisation += Arrays.stream(membership.getTags()).anyMatch(tag -> tag.startsWith(ORGANISATION_TAG_PREFIX)) ? 1 : 0;
        }


        Map<String, Integer> results = new LinkedHashMap<>();

        results.put("EMAIL", groupSize > 0 ? (int) ((hasEmail / groupSize) * 100) : 0);
        results.put("PHONE", groupSize > 0 ? (int) ((hasPhoneNumber / groupSize) * 100) : 0);
        results.put("PROVINCE", groupSize > 0 ? (int) ((hasProvince / groupSize) * 100) : 0);
        results.put("ORGANISATION", groupSize > 0 ? (int) ((hasOrganisation / groupSize) * 100) : 0);

        cache.put(new Element(cacheKey, results));

        return results;
    }

    @Override
    public Map<String, Integer> getTopicInterestStatsPercentage(String groupUid) {
        Map<String, Integer> topicInterests = new HashMap<>(getTopicInterestStats(groupUid, false));

        List<Membership> memberships = membershipRepository.findAll(MembershipSpecifications.forGroup(groupUid));

        double membersCount = memberships.size();
        topicInterests.entrySet().forEach(entry -> {
            if (membersCount > 0)
                entry.setValue((int) (100 * entry.getValue() / membersCount));
            else entry.setValue(0);
        });

        return topicInterests;
    }

    @Override
    public Map<String, Integer> getTopicInterestStatsRaw(String groupUid, Boolean clearCache) {
        return getTopicInterestStats(groupUid, clearCache);
    }

    private Map<String, Integer> getTopicInterestStats(String groupUid, Boolean clearCache) {

        Cache cache = cacheManager.getCache("group_stats_topic_interests");
        String cacheKey = groupUid;
        Element element = cache.get(cacheKey);
        Map<String, Integer> resultFromCache = element != null ? (Map<String, Integer>) element.getObjectValue() : null;

        if(clearCache) {
            cache.remove(cacheKey);
        }else {
            if (resultFromCache != null)
                return resultFromCache;
        }


        List<Membership> memberships = membershipRepository.findAll(MembershipSpecifications.forGroup(groupUid));

        Map<String, Integer> topicInterests = new LinkedHashMap<>();

        for (Membership membership : memberships) {
            for (String topic : membership.getTopics()) {
                int count = (topicInterests.containsKey(topic) ? topicInterests.get(topic) : 0) + 1;
                topicInterests.put(topic, count);
            }
        }

        cache.put(new Element(cacheKey, topicInterests));

        return topicInterests;
    }


}

