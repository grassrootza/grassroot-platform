package za.org.grassroot.webapp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.webapp.enums.USSDSection;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * Created by luke on 2015/12/04.
 */
public class USSDUrlUtil {

    private static final Logger log = LoggerFactory.getLogger(USSDUrlUtil.class);

    public static final String homePath = "/ussd/";

    // note: public static here is not great, but request param notations are not happy with getters, hence leaving for now
    public static final String
            phoneNumber = "msisdn",
            userInputParam = "request",
            groupUidParam = "groupUid",
            entityUidParam = "entityUid",
            previousMenu = "prior_menu",
            yesOrNoParam = "confirmed",
            interruptedFlag = "interrupted",
            interruptedInput = "prior_input",
            revisingFlag = "revising"; // may want to change this last one

    public static final String
            groupUidUrlSuffix = ("?" + groupUidParam + "="),
            entityUidUrlSuffix = ("?" + entityUidParam + "="),
            setInterruptedFlag = "&" + interruptedFlag + "=1",
            setRevisingFlag = "&" + revisingFlag + "=1",
            addInterruptedInput = "&" + interruptedInput + "=";

    /**
     *
     * @param menuPrompt
     * @param existingGroupUri
     * @param newGroupUri
     * @param section
     *@param pageNumber  @return
     */
    public static String paginatedGroupUrl(String menuPrompt, String existingGroupUri, String newGroupUri, USSDSection section, Integer pageNumber) {
        String newGroupParameter = (newGroupUri != null) ? "&newUri=" + encodeParameter(newGroupUri) : "";
        return "group_page?prompt=" + encodeParameter(menuPrompt) + "&existingUri=" + encodeParameter(existingGroupUri)
                + newGroupParameter + "&page=" + pageNumber + (section != null ? "&section=" + section.name() : "");
    }

    public static String paginatedEventUrl(String menuPrompt, USSDSection section, String viewEventUrl,
                                           String menuForNew, String optionTextForNew, int pastPresentBoth, boolean includeGroupName, Integer pageNumber) {
        String newEventParameter = (menuForNew != null) ? "&newMenu=" + encodeParameter(menuForNew) + "&newOption=" +
                encodeParameter(optionTextForNew) : "";
        return "event_page?section=" + section.toString() + "&prompt=" + encodeParameter(menuPrompt) +
                "&nextUrl=" + encodeParameter(viewEventUrl) + newEventParameter +
                "&pastPresentBoth=" + pastPresentBoth + "&includeGroupName=" + includeGroupName + "&page=" + pageNumber;
    }

    public static String encodeParameter(String stringToEncode) {
        try {
            return URLEncoder.encode(stringToEncode, "UTF-8");
        } catch (UnsupportedEncodingException e) { // todo: handle errors better
            return stringToEncode;
        }
    }

    public static String decodeParameter(String stringToDecode) {
        try {
            return URLDecoder.decode(stringToDecode, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return stringToDecode;
        }
    }

    // note: this expects the entityId string fully formed (e.g., "groupId=1"), else have to create many duplicate methods
    public static String saveMenuUrlWithInput(USSDSection section, String menu, String entityId, String input) {
        String divisor = (entityId == null || entityId.equals("")) ? "?" : entityId; // if we are passed groupId=1, or similar, it comes with the "?" character
        String inputToSave = (input == null) ? "" : encodeParameter(input);
        return section.toPath() + menu + divisor + setInterruptedFlag + "&" + interruptedInput + "=" + inputToSave;
    }

    public static String saveMeetingMenu(String menu, String entityUid, boolean revising) {
        String revisingFlag = (revising) ? "&revising=1" : "";
        return USSDSection.MEETINGS.toPath() + menu + "?entityUid=" + entityUid + setInterruptedFlag + revisingFlag;
    }

    public static String saveGroupMenu(String menu, String groupUid) {
        return USSDSection.GROUP_MANAGER.toPath() + menu + "?groupUid=" + groupUid + setInterruptedFlag;
    }

    public static String saveGroupMenuWithParams(String menu, String groupUid, String params) {
        return USSDSection.GROUP_MANAGER.toPath() + menu + "?groupUid=" + groupUid + setInterruptedFlag + params;
    }

    public static String saveGroupMenuWithInput(String menu, String groupUid, String input, boolean safetyGroup) {
        if (!safetyGroup) {
            return USSDSection.GROUP_MANAGER.toPath() + menu + "?groupUid=" + groupUid + addInterruptedInput + encodeParameter(input);
        } else {
            return USSDSection.SAFETY_GROUP_MANAGER.toPath() + menu + "?groupUid=" + groupUid + addInterruptedInput + encodeParameter(input);
        }
    }

    public static String saveVoteMenu(String menu, String requestUid) {
        return USSDSection.VOTES.toPath() + menu + "?requestUid=" + requestUid + setInterruptedFlag;
    }

    public static String backVoteUrl(String menu, String requestUid) {
        return USSDSection.VOTES.toPath() + menu + "?requestUid=" + requestUid + setRevisingFlag;
    }

    public static String saveToDoMenu(String menu, String todoUid) {
        return USSDSection.TODO.toPath() + menu + "?todoUid=" + todoUid + setInterruptedFlag;
    }

    public static String saveSafetyMenuPrompt(String menu) {
        return USSDSection.SAFETY_GROUP_MANAGER.toPath() + menu + "?" + interruptedFlag + "=1";
    }

    public static String saveAddressMenu(String menu, String field) {
        return USSDSection.SAFETY_GROUP_MANAGER.toPath() + menu + "?field=" + field + setInterruptedFlag;
    }

    public static String saveSafetyGroupMenu(String menu, String groupUid, String input) {
        return USSDSection.SAFETY_GROUP_MANAGER.toPath() + menu + "?groupUid=" + groupUid + setInterruptedFlag
                + ((input != null) ? "&prior_input=" + input : "");
    }

    public static String mtgMenu(String menu, String entityUid) {
        return USSDSection.MEETINGS.toPath() + menu + "?entityUid=" + entityUid;
    }

    public static String editingMtgMenuUrl(String menu, String eventUid, String requestUid, String action) {
        return USSDSection.MEETINGS.toPath() + menu + "?entityUid=" + eventUid + "&requestUid=" + requestUid
                + "&action=" + action;
    }

    public static String groupMenuWithId(String menu, String groupUid) {
        return USSDSection.GROUP_MANAGER.toPath() + menu + "?groupUid=" + groupUid;
    }

    public static String groupMenuWithId(USSDSection section, String menu, String groupUid) {
        return section.toPath() + menu + "?groupUid=" + groupUid;
    }

    public static String safetyMenuWithId(String menu, String safetyUid) {
        return USSDSection.SAFETY_GROUP_MANAGER.toPath() + menu + "-do" + "?entityUid=" + safetyUid + "&response=";
    }

    public static String groupVisibilityOption(String menu, String groupUid, boolean discoverable) {
        return USSDSection.GROUP_MANAGER.toPath() + menu + "?groupUid=" + groupUid + "&hide=" + discoverable;
    }

    public static String todosViewGroupCompleteEntries(String menu, String groupUid, Integer pageNumber) {
        return USSDSection.TODO.toPath() + menu + "?groupUid=" + groupUid + "&pageNumber=" + pageNumber;
    }

    public static String approveRejectRequestMenuUrl(String menu, String userUid, String requestUid) {
        return USSDSection.GROUP_MANAGER.toPath() + menu + "-do" + "?requestUid=" + requestUid + "&userUid=" + userUid;
    }

}
