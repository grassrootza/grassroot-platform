package za.org.grassroot.webapp.controller.ussd.group;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.account.AccountFeaturesBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.task.VoteBroker;
import za.org.grassroot.webapp.controller.ussd.USSDBaseController;
import za.org.grassroot.webapp.controller.ussd.USSDVoteController;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.enums.USSDSection.HOME;

// not strictly a controller, but all its methods used in one
@Slf4j @RestController
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDGroupJoinController extends USSDBaseController  {

    private final GroupBroker groupBroker;
    private final GroupQueryBroker groupQueryBroker;

    private VoteBroker voteBroker;
    private USSDVoteController voteController;
    private AccountFeaturesBroker accountFeaturesBroker;

    @Autowired
    public USSDGroupJoinController(GroupBroker groupBroker, GroupQueryBroker groupQueryBroker) {
        this.groupBroker = groupBroker;
        this.groupQueryBroker = groupQueryBroker;
    }

    @Autowired
    public void setVoteBroker(VoteBroker voteBroker) {
        this.voteBroker = voteBroker;
    }

    @Autowired
    public void setUssdVoteController(USSDVoteController ussdVoteController) {
        this.voteController = ussdVoteController;
    }

    @Autowired
    public void setAccountFeaturesBroker(AccountFeaturesBroker accountFeaturesBroker) {
        this.accountFeaturesBroker = accountFeaturesBroker;
    }

    // with mass votes this may get tricky, though most of it involves very fast & indexed select or count queries,
    // but still adding in timing so that we can watch out
    public USSDMenu lookForJoinCode(User user, String trailingDigits) {
        long startTime = System.currentTimeMillis();
        Optional<Group> searchResult = groupQueryBroker.findGroupFromJoinCode(trailingDigits.trim());
        if (!searchResult.isPresent())
            return null;

        Group group = searchResult.get();
        log.debug("adding user via join code ... {}", trailingDigits);
        if (accountFeaturesBroker.numberMembersLeftForGroup(group.getUid(), GroupJoinMethod.USSD_JOIN_CODE) == 0) {
            return notifyGroupLimitReached(user, group);
        }

        Membership membership = groupBroker.addMemberViaJoinCode(user.getUid(), group.getUid(), trailingDigits, UserInterfaceType.USSD);
        USSDMenu menu;
        if (group.getJoinTopics() != null && !group.getJoinTopics().isEmpty() && !membership.hasAnyTopic(group.getJoinTopics())) {
            menu = askForJoinTopics(group, user);
        } else if (voteBroker.hasMassVoteOpen(group.getUid())) {
            menu = respondToMassVoteMenu(group, user);
        } else {
            String promptStart = (group.hasName()) ? getMessage(HOME, startMenu, promptKey + ".group.token.named", group.getGroupName(), user) :
                    getMessage(HOME, startMenu, promptKey + ".group.token.unnamed", user);
            menu = setUserProfile(user, promptStart);
        }
        log.info("Completed use of group join code, time taken : {} msecs", System.currentTimeMillis() - startTime);
        return menu;
    }

    private USSDMenu notifyGroupLimitReached(User user, Group group) {
        USSDMenu menu = new USSDMenu(getMessage("group.join.limit.exceeded", new String[] { group.getName() }, user));
        menu.addMenuOptions(optionsHomeExit(user, false));
        return menu;
    }

    private USSDMenu askForJoinTopics(Group group, User user) {
        final String prompt = getMessage(HOME, startMenu, promptKey + ".group.topics", group.getName(), user);
        final String urlBase = "group/join/topics?groupUid=" + group.getUid() + "&topic=";
        USSDMenu menu = new USSDMenu(prompt);
        group.getJoinTopics().forEach(topic -> menu.addMenuOption(urlBase + USSDUrlUtil.encodeParameter(topic), topic));
        return menu;
    }

    private USSDMenu respondToMassVoteMenu(Group group, User user) {
        Vote massVote = voteBroker.getMassVoteForGroup(group.getUid());
        final String prompt = getMessage("home.start.prompt.group.vote", new String[] { group.getName(), massVote.getName() }, user);
        USSDMenu menu = voteController.assembleVoteMenu(user, massVote); // handles option setting, etc.
        menu.setPromptMessage(prompt); // because this is better in this context than standard
        log.info("Created a mass vote, here it is: {}", menu);
        return menu;
    }

    @RequestMapping(value = homePath + "group/join/topics")
    @ResponseBody
    public Request setJoinTopics(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam String groupUid,
                                 @RequestParam String topic) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        groupBroker.setMemberJoinTopics(user.getUid(), groupUid, user.getUid(), Collections.singletonList(topic));
        String prompt = getMessage(HOME, startMenu, "prompt.group.topics.set", topic, user);
        return menuBuilder(setUserProfile(user, prompt));
    }

    @RequestMapping(value = homePath + "group/join/profile")
    @ResponseBody public Request setUserProfileMenu(@RequestParam(value = phoneNumber) String inputNumber,
                                                    @RequestParam(value = "field") String field,
                                                    @RequestParam(value = "province", required = false) Province province,
                                                    @RequestParam(value = "language", required = false) Locale language,
                                                    @RequestParam(value = userInputParam, required = false) String name) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);

        if ("PROVINCE".equals(field)) {
            userManager.updateUserProvince(user.getUid(), province);
        } else if ("LANGUAGE".equals(field)) {
            userManager.updateUserLanguage(user.getUid(), language, UserInterfaceType.USSD);
        } else if ("NAME".equals(field) && !StringUtils.isEmpty(name) && name.length() > 1) {
            userManager.updateDisplayName(user.getUid(), user.getUid(), name);
        }

        String prompt = getMessage("home.start.profile.step", user);
        User updatedUser = userManager.load(user.getUid());
        log.info("updated user, now set as : {}", updatedUser);
        return menuBuilder(setUserProfile(updatedUser, prompt));
    }

    private USSDMenu setUserProfile(User user, String promptStart) {
        String promptSuffix;
        log.info("does user have language ? : {}, lang code = {}", user.hasLanguage(), user.getLanguageCode());
        if (!user.hasLanguage()) {
            promptSuffix = getMessage("home.start.prompt.language", user);
            return new USSDMenu(promptStart + " " + promptSuffix, languageOptions("group/join/profile?field=LANGUAGE&language="));
        } else if (user.getProvince() == null) {
            promptSuffix = getMessage("home.start.prompt.province", user);
            return new USSDMenu(promptStart + " " + promptSuffix, provinceOptions(user, "group/join/profile?field=PROVINCE&province="));
        } else {
            promptSuffix = getMessage("home.start.prompt.choose", user);
            return !userManager.needsToSetName(user, false) ? welcomeMenu(promptStart + " " + promptSuffix, user) :
                    new USSDMenu(getMessage(HOME, USSDBaseController.startMenu, promptKey + "-rename.short", user), "rename-start");
        }
    }
}
