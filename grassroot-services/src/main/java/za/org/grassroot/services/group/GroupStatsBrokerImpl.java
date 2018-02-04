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
import za.org.grassroot.integration.socialmedia.ManagedPagesResponse;
import za.org.grassroot.integration.socialmedia.SocialMediaBroker;

import javax.annotation.Nullable;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class GroupStatsBrokerImpl implements GroupStatsBroker {

    private final GroupLogRepository groupLogRepository;
    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final CacheManager cacheManager;
    private final SocialMediaBroker socialMediaBroker;

    private final String ORGANISATION_TAG_PREFIX = "AFFILIATION";

    public GroupStatsBrokerImpl(
            GroupLogRepository groupLogRepository,
            GroupRepository groupRepository,
            MembershipRepository membershipRepository,
            CacheManager cacheManager,
            SocialMediaBroker socialMediaBroker) {

        this.groupLogRepository = groupLogRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.cacheManager = cacheManager;
        this.socialMediaBroker = socialMediaBroker;
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
        Map<String, Long> data = memberships.stream()
                .filter(m -> !m.getAffiliations().isEmpty())
                .flatMap(m -> m.getAffiliations().stream())
                .collect(Collectors.groupingBy(
                        s -> s,
                        Collectors.counting()
                ));
        data.put("Unknown", (long) memberships.size());

        cache.put(new Element(cacheKey, data));

        return data;
    }


    @Override
    public Map<String, Integer> getMemberDetailsStats(String groupUid) {

        Cache cache = cacheManager.getCache("group_stats_member-details");
        String cacheKey = groupUid;
        Element element = cache.get(cacheKey);
        Map<String, Integer> resultFromCache = element != null ? (Map<String, Integer>) element.getObjectValue() : null;

        if (resultFromCache != null)
            return resultFromCache;

        List<Membership> memberships = membershipRepository.findAll(MembershipSpecifications.forGroup(groupUid));

        double hasEmail = 0;
        double hasPhoneNumber = 0;
        double hasProvince = 0;
        double hasOrganisation = 0;
        double hasFacebook = 0;
        double hasTwitter = 0;

        Set<String> memberUids = memberships.stream().map(m -> m.getUser().getUid()).collect(Collectors.toSet());

        for (Membership membership : memberships) {
            hasEmail += membership.getUser().hasEmailAddress() ? 1 : 0;
            hasPhoneNumber += membership.getUser().hasPhoneNumber() ? 1 : 0;
            hasProvince += membership.getUser().getProvince() != null ? 1 : 0;
            hasOrganisation += Arrays.stream(membership.getTags()).anyMatch(tag -> tag.startsWith(ORGANISATION_TAG_PREFIX)) ? 1 : 0;
            // since, by definition, user cannot have soc media integrations if haven't set up and registered (i.e., if a USSD only
            // user, this is a quick way to filter out the unneeded calls)
            if (membership.getUser().isHasWebProfile()) {
                Map<String, ManagedPagesResponse> currentIntegrations = socialMediaBroker.getCurrentIntegrations(membership.getUser().getUid()).getCurrentIntegrations();
                hasFacebook += currentIntegrations.containsKey("facebook") ? 1 : 0;
                hasTwitter += currentIntegrations.containsKey("twitter") ? 1 : 0;
            }
        }


        Map<String, Integer> results = new LinkedHashMap<>();

        results.put("EMAIL", memberships.size() > 0 ? (int) ((hasEmail / memberships.size()) * 100) : 0);
        results.put("PHONE", memberships.size() > 0 ? (int) ((hasPhoneNumber / memberships.size()) * 100) : 0);
        results.put("PROVINCE", memberships.size() > 0 ? (int) ((hasProvince / memberships.size()) * 100) : 0);
        results.put("ORGANISATION", memberships.size() > 0 ? (int) ((hasOrganisation / memberships.size()) * 100) : 0);
        results.put("FACEBOOK", memberships.size() > 0 ? (int) ((hasFacebook / memberships.size()) * 100) : 0);
        results.put("TWITTER", memberships.size() > 0 ? (int) ((hasTwitter / memberships.size()) * 100) : 0);

        cache.put(new Element(cacheKey, results));

        return results;
    }

    @Override
    public Map<String, Integer> getTopicInterestStats(String groupUid) {

        Cache cache = cacheManager.getCache("group_stats_topic_interests");
        String cacheKey = groupUid;
        Element element = cache.get(cacheKey);
        Map<String, Integer> resultFromCache = element != null ? (Map<String, Integer>) element.getObjectValue() : null;

        if (resultFromCache != null)
            return resultFromCache;

        List<Membership> memberships = membershipRepository.findAll(MembershipSpecifications.forGroup(groupUid));

        Map<String, Integer> topicInterests = new LinkedHashMap<>();

        for (Membership membership : memberships) {
            for (String topic : membership.getTopics()) {
                int count = (topicInterests.containsKey(topic) ? topicInterests.get(topic) : 0) + 1;
                topicInterests.put(topic, count);
            }
        }

        double membersCount = memberships.size();
        topicInterests.entrySet().forEach(entry -> {
            if (membersCount > 0)
                entry.setValue((int) (100 * entry.getValue() / membersCount));
            else entry.setValue(0);
        });

        cache.put(new Element(cacheKey, topicInterests));

        return topicInterests;
    }

}

