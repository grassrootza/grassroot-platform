package za.org.grassroot.services.group;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.MembershipRepository;
import za.org.grassroot.core.specifications.GroupLogSpecifications;
import za.org.grassroot.core.specifications.MembershipSpecifications;

import javax.annotation.Nullable;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class GroupStatsBrokerImpl implements GroupStatsBroker {

    private final GroupLogRepository groupLogRepository;
    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final CacheManager cacheManager;


    public GroupStatsBrokerImpl(
            GroupLogRepository groupLogRepository,
            GroupRepository groupRepository,
            MembershipRepository membershipRepository,
            CacheManager cacheManager) {

        this.groupLogRepository = groupLogRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.cacheManager = cacheManager;
    }

    @Override
    public Map<String, Integer> getMembershipGrowthStats(String groupUid, @Nullable Integer year, @Nullable Integer month) {

        Cache cache = cacheManager.getCache("group_stats_member_count");
        String cacheKey = groupUid + "-" + year + "-" + month;
        Element element = cache.get(cacheKey);
        Map<String, Integer> resultFromCache = element != null ? (Map<String, Integer>) element.getObjectValue() : null;

        if (resultFromCache != null)
            return resultFromCache;

        // if there is no cached stats, calculate them, put in cache and return

        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");

        Group group = groupRepository.findOneByUid(groupUid);

        LocalDate startTime = getStartTime(year, month, group);
        LocalDate endTime = getEndTime(year, month, startTime);

        Instant startTimeInstant = startTime.atStartOfDay(Clock.systemDefaultZone().getZone()).toInstant();
        Instant endTimeInstant = endTime.atStartOfDay(Clock.systemDefaultZone().getZone()).toInstant();

        Specifications<GroupLog> groupLogSpec = GroupLogSpecifications.memberCountChanges(groupUid, startTimeInstant, endTimeInstant);
        List<GroupLog> memberCountChanges = groupLogRepository.findAll(groupLogSpec);

        Specifications<Membership> startingMemberCountSpec = Specifications.where(
                MembershipSpecifications.forGroup(group))
                .and(MembershipSpecifications.membersJoinedBefore(startTimeInstant));

        int currentMemberCount = (int) membershipRepository.count(startingMemberCountSpec);

        if (year != null && month != null) {

            Map<String, List<GroupLog>> changes = memberCountChanges.stream()
                    .collect(
                            Collectors.groupingBy(gl -> dayFormatter.format(gl.getCreatedDateTime().atZone(Clock.systemDefaultZone().getZone()))
                            ));

            Map<String, Integer> membersCountByDay = new LinkedHashMap<>(); //preserve key order
            LocalDate currDay = startTime;
            while (currDay.isBefore(endTime)) {

                String dayKey = dayFormatter.format(currDay);
                List<GroupLog> dayChanges = changes.get(dayKey);

                if (dayChanges != null) {
                    int added = (int) dayChanges.stream().filter(gl -> gl.getGroupLogType() != GroupLogType.GROUP_MEMBER_REMOVED).count();
                    int removed = (int) dayChanges.stream().filter(gl -> gl.getGroupLogType() == GroupLogType.GROUP_MEMBER_REMOVED).count();
                    currentMemberCount += (added - removed);
                }
                membersCountByDay.put(dayKey, currentMemberCount);
                currDay = currDay.plusDays(1);
            }

            cache.put(new Element(cacheKey, membersCountByDay));
            return membersCountByDay;
        } else {

            Map<String, List<GroupLog>> changes = memberCountChanges.stream()
                    .collect(
                            Collectors.groupingBy(gl -> monthFormatter.format(gl.getCreatedDateTime().atZone(Clock.systemDefaultZone().getZone()))
                            ));

            Map<String, Integer> membersCountByMonth = new LinkedHashMap<>(); //preserve key order
            LocalDate currMonth = startTime;
            while (currMonth.getYear() < endTime.getYear() || currMonth.getMonthValue() < endTime.getMonthValue()) {
                String monthKey = monthFormatter.format(currMonth);
                List<GroupLog> monthChanges = changes.get(monthKey);
                if (monthChanges != null) {
                    int added = (int) monthChanges.stream().filter(gl -> gl.getGroupLogType() != GroupLogType.GROUP_MEMBER_REMOVED).count();
                    int removed = (int) monthChanges.stream().filter(gl -> gl.getGroupLogType() == GroupLogType.GROUP_MEMBER_REMOVED).count();
                    currentMemberCount += (added - removed);
                }
                membersCountByMonth.put(monthKey, currentMemberCount);
                currMonth = currMonth.plusMonths(1);
            }

            cache.put(new Element(cacheKey, membersCountByMonth));
            return membersCountByMonth;

        }

    }


    private LocalDate getEndTime(@Nullable Integer year, @Nullable Integer month, LocalDate startTime) {

        if (year != null && month != null) {
            LocalDate endDay = startTime.plus(1, ChronoUnit.MONTHS);
            LocalDate thisDay = LocalDate.now();
            if (thisDay.isBefore(endDay)) {
                endDay = LocalDate.of(thisDay.getYear(), thisDay.getMonthValue(), thisDay.getDayOfMonth()).plusDays(1);
            }
            return endDay;
        } else if (year != null) {
            LocalDate endMonth = startTime.plus(1, ChronoUnit.YEARS);
            LocalDate thisMOnth = LocalDate.now();
            thisMOnth = LocalDate.of(thisMOnth.getYear(), thisMOnth.getMonthValue(), 1);
            if (thisMOnth.isBefore(endMonth))
                endMonth = LocalDate.of(thisMOnth.getYear(), thisMOnth.getMonthValue(), 1).plusMonths(1);
            return endMonth;
        } else {
            LocalDate today = LocalDate.now();
            return LocalDate.of(today.getYear(), today.getMonth(), 1).plusMonths(1);
        }
    }

    private LocalDate getStartTime(@Nullable Integer year, @Nullable Integer month, Group group) {

        if (year != null && month != null)
            return LocalDate.of(year, month, 1);

        else if (year != null)
            return LocalDate.of(year, 1, 1);
        else
            return group.getCreatedDateTime().atZone(Clock.systemDefaultZone().getZone()).toLocalDate();

    }

    @Override
    public Map<String, Long> getProvincesStats(String groupUid) {

        Cache cache = cacheManager.getCache("group_stats_provinces");
        String cacheKey = groupUid;
        Element element = cache.get(cacheKey);
        Map<String, Long> resultFromCache = element != null ? (Map<String, Long>) element.getObjectValue() : null;

        if (resultFromCache != null)
            return resultFromCache;

        List<Membership> memberships = membershipRepository.findAll(MembershipSpecifications.forGroup(groupUid));

        Map<String, Long> data = memberships.stream()
                .filter(m -> m.getUser().getProvince() != null)
                .collect(
                        Collectors.groupingBy(
                                m -> m.getUser().getProvince().name(),
                                Collectors.counting()
                        )
                );

        long unknownProvinceCount = memberships.stream().filter(m -> m.getUser().getProvince() == null).count();
        if (unknownProvinceCount > 0)
            data.put("UNKNOWN", unknownProvinceCount);

        cache.put(new Element(cacheKey, data));

        return data;
    }

    @Override
    public Map<String, Long> getSourcesStats(String groupUid) {


        Cache cache = cacheManager.getCache("group_stats_sources");
        String cacheKey = groupUid;
        Element element = cache.get(cacheKey);
        Map<String, Long> resultFromCache = element != null ? (Map<String, Long>) element.getObjectValue() : null;

        if (resultFromCache != null)
            return resultFromCache;


        List<Membership> memberships = membershipRepository.findAll(MembershipSpecifications.forGroup(groupUid));

        Map<String, Long> data = memberships.stream()
                .filter(m -> m.getJoinMethod() != null)
                .collect(
                        Collectors.groupingBy(
                                m -> m.getJoinMethod().name(),
                                Collectors.counting()
                        )
                );

        long unknownSourceCount = memberships.stream().filter(m -> m.getJoinMethod() == null).count();
        if (unknownSourceCount > 0)
            data.put("UNKNOWN", unknownSourceCount);

        cache.put(new Element(cacheKey, data));

        return data;
    }


    @Override
    public Map<String, Long> getOrganisationsStats(String groupUid) {

        Cache cache = cacheManager.getCache("group_stats_organisations");
        String cacheKey = groupUid;
        Element element = cache.get(cacheKey);
        Map<String, Long> resultFromCache = element != null ? (Map<String, Long>) element.getObjectValue() : null;

        if (resultFromCache != null)
            return resultFromCache;

        List<Membership> memberships = membershipRepository.findAll(MembershipSpecifications.forGroup(groupUid));
        //todo implement logic here
        Map<String, Long> data = new HashMap<>();
        data.put("Other", (long) memberships.size());

        cache.put(new Element(cacheKey, data));

        return data;
    }
}

