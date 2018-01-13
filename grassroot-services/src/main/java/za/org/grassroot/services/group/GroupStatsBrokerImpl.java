package za.org.grassroot.services.group;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class GroupStatsBrokerImpl implements GroupStatsBroker {

    private final GroupLogRepository groupLogRepository;
    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;

    public GroupStatsBrokerImpl(GroupLogRepository groupLogRepository, GroupRepository groupRepository, MembershipRepository membershipRepository) {
        this.groupLogRepository = groupLogRepository;
        this.groupRepository = groupRepository;

        this.membershipRepository = membershipRepository;
    }

    @Override
    public Map<String, Integer> getMembershipGrowthStats(String groupUid, @Nullable Integer year, @Nullable Integer month) {

        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");

        Group group = groupRepository.findOneByUid(groupUid);

        LocalDate startTime = getStartTime(year, month, group);
        LocalDate endTime = getEndTime(year, month, startTime);

        Instant startTimeInstant = startTime.atStartOfDay(Clock.systemDefaultZone().getZone()).toInstant();
        Instant endTimeInstant = endTime.atStartOfDay(Clock.systemDefaultZone().getZone()).toInstant();
        ;

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
}
