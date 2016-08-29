package za.org.grassroot.webapp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.webapp.enums.USSDSection;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;

/**
 * Created by luke on 2015/12/04.
 */
public class USSDUrlUtil {

    private static final Logger log = LoggerFactory.getLogger(USSDUrlUtil.class);

    public static final String homePath = "/ussd/";

    // todo: consider using getters (may cause issues with @RequestParam annotation requiring constants though)
    public static final String
            phoneNumber = "msisdn",
            userInputParam = "request",
            groupUidParam = "groupUid",
            logbookIDParam = "logbookUid",
            entityUidParam = "entityUid",
            previousMenu = "prior_menu",
            yesOrNoParam = "confirmed",
            interruptedFlag = "interrupted",
            interruptedInput = "prior_input",
            revisingFlag = "revising"; // may want to change this last one

    public static final String
            groupUidUrlSuffix = ("?" + groupUidParam + "="),
            logbookIdUrlSuffix = ("?" + logbookIDParam + "="),
            entityUidUrlSuffix = ("?" + entityUidParam + "="),
            setInterruptedFlag = "&" + interruptedFlag + "=1",
            setRevisingFlag = "&" + revisingFlag + "=1",
            addInterruptedInput = "&" + interruptedInput + "=";

    /**
     *
     * @param menuPrompt
     * @param existingGroupUri
     * @param newGroupUri
     * @param pageNumber
     * @return
     */
    public static String paginatedGroupUrl(String menuPrompt, String existingGroupUri, String newGroupUri, Integer pageNumber) {
        String newGroupParameter = (newGroupUri != null) ? "&newUri=" + encodeParameter(newGroupUri) : "";
        return "group_page?prompt=" + encodeParameter(menuPrompt) + "&existingUri=" + encodeParameter(existingGroupUri)
                + newGroupParameter + "&page=" + pageNumber;
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

    public static String createInterruptedString(String path, Map<String, String> parameters) {
        // note: this might be more efficient with a URLBuilder or the like, but those (AFAIK) impose fully written paths
        StringBuilder urlBuilder = new StringBuilder(path + "?");
        for (Map.Entry<String, String> parameter : parameters.entrySet())
            urlBuilder.append(parameter.getKey()).append("=").append(encodeParameter(parameter.getValue()));
        return urlBuilder.toString();
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
        return USSDSection.VOTES.toPath() + menu + "?entityUid=" + requestUid + setInterruptedFlag;
    }

    public static String backVoteUrl(String menu, String requestUid) {
        return USSDSection.VOTES.toPath() + menu + "?entityUid=" + requestUid + setRevisingFlag;
    }

    public static String saveToDoMenu(String menu, String logBookUid) {
        return USSDSection.TODO.toPath() + menu + "?logbookUid=" + logBookUid + setInterruptedFlag;
    }

    public static String saveToDoMenu(String menu, String logBookUid, String priorInput) {
        return saveToDoMenu(menu, logBookUid) + addInterruptedInput + encodeParameter(priorInput);
    }

    // todo: use this above, or figure out a more elegant way around it
    public static String saveToDoMenu(String menu, String logBookUid, String priorInput, boolean encodeInput) {
        return saveToDoMenu(menu, logBookUid) + addInterruptedInput + (encodeInput ? encodeParameter(priorInput) : priorInput);
    }

    public static String saveToDoMenu(String menu, String logBookUid, String previousMenu, String priorInput, boolean encodeInput) {
        return saveToDoMenu(menu, logBookUid, priorInput, encodeInput) + "&prior_menu=" + previousMenu;

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

    public static String safetyMenuWithId(String menu, String safetyUid, boolean response) {
        return USSDSection.SAFETY_GROUP_MANAGER.toPath() + menu + "-do" + "?entityUid=" + safetyUid + "&response=" + response;
    }

    public static String safetyMenuWithId(String menu, String safetyUid, String response) {
        return USSDSection.SAFETY_GROUP_MANAGER.toPath() + menu + "-do" + "?entityUid=" + safetyUid + "&response=" + response;
    }


    public static String groupVisibilityOption(String menu, String groupUid, boolean discoverable) {
        return USSDSection.GROUP_MANAGER.toPath() + menu + "?groupUid=" + groupUid + "&hide=" + discoverable;
    }

    public static String logViewExistingUrl(String menu, String groupUid, Boolean done, Integer pageNumber) {
        return USSDSection.TODO.toPath() + menu + "?groupUid=" + groupUid + "&done=" + done + "&pageNumber=" + pageNumber;
    }

    public static String approveRejectRequestMenuUrl(String menu, String userUid, String requestUid) {
        return USSDSection.GROUP_MANAGER.toPath() + menu + "-do" + "?requestUid=" + requestUid + "&userUid=" + userUid;
    }

}
