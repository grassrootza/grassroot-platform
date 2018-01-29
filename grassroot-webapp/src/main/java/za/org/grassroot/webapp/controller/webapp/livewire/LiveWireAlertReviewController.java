package za.org.grassroot.webapp.controller.webapp.livewire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.services.livewire.DataSubscriberBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.services.livewire.LiveWireSendingBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by luke on 2017/05/13.
 */
@Controller
@RequestMapping("/livewire/alert")
@PreAuthorize("hasRole('ROLE_LIVEWIRE_USER')")
@PropertySource(value = "${grassroot.integration.properties}", ignoreResourceNotFound = true)
public class LiveWireAlertReviewController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(LiveWireAlertReviewController.class);

    private final LiveWireAlertBroker liveWireAlertBroker;
    private final LiveWireSendingBroker liveWireSendingBroker;
    private final DataSubscriberBroker dataSubscriberBroker;
    private final StorageBroker storageBroker;
    private final MediaFileBroker mediaFileBroker;

    @Autowired
    public LiveWireAlertReviewController(LiveWireAlertBroker liveWireAlertBroker, LiveWireSendingBroker liveWireSendingBroker, DataSubscriberBroker dataSubscriberBroker, StorageBroker storageBroker, MediaFileBroker mediaFileBroker) {
        this.liveWireAlertBroker = liveWireAlertBroker;
        this.liveWireSendingBroker = liveWireSendingBroker;
        this.dataSubscriberBroker = dataSubscriberBroker;
        this.storageBroker = storageBroker;
        this.mediaFileBroker = mediaFileBroker;
    }

    @RequestMapping(value = {"/", ""}, method = RequestMethod.GET)
    public String liveWireUserIndex(Model model) {
        populateModel(new PageRequest(0, 5, Sort.Direction.DESC, "creationTime"), false, model);
        return "livewire/alerts";
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public String displayLiveWireAlerts(@RequestParam(required = false) Boolean onlyUnreviewed,
                                        @PageableDefault(page = 0, size = 5)
                                        @SortDefault.SortDefaults({
                                                @SortDefault(sort = "creationTime", direction = Sort.Direction.DESC),
                                                @SortDefault(sort = "type", direction = Sort.Direction.ASC)
                                        }) Pageable pageable, Model model) {
        populateModel(pageable, onlyUnreviewed == null ? false : onlyUnreviewed, model);
        return "livewire/alerts";
    }

    private void populateModel(Pageable pageable, boolean unreviewedOnly, Model model) {
        Page<LiveWireAlert> alerts = liveWireAlertBroker.loadAlerts(getUserProfile().getUid(),
                unreviewedOnly, pageable);
        model.addAttribute("alerts", alerts);
        model.addAttribute("pageable", pageable);
        model.addAttribute("sort", pageable.getSort().iterator().next());
        model.addAttribute("canTag", liveWireAlertBroker.canUserTag(getUserProfile().getUid()));
        boolean canRelease = liveWireAlertBroker.canUserRelease(getUserProfile().getUid());
        model.addAttribute("canRelease", canRelease);
        if (canRelease) {
            model.addAttribute("publicLists", dataSubscriberBroker.listPublicSubscribers());
        }
    }

    @RequestMapping(value = "view", method = RequestMethod.GET)
    public String viewLiveWireAlert(@RequestParam String alertUid, Model model) {
        final LiveWireAlert alert = liveWireAlertBroker.load(alertUid);
        model.addAttribute("alert", alert);
        model.addAttribute("backingEntityDesc", describeBackingEntity(alert));
        model.addAttribute("status", !alert.isReviewed() ? "Unreviewed" :
                alert.isSent() ? "Sent" : "Blocked");

        boolean canRelease = liveWireAlertBroker.canUserRelease(getUserProfile().getUid());
        model.addAttribute("canRelease", canRelease && !alert.isSent());
        if (canRelease) {
            model.addAttribute("publicLists", dataSubscriberBroker.listPublicSubscribers());
        }

        model.addAttribute("canTag", liveWireAlertBroker.canUserTag(getUserProfile().getUid()));
        return "livewire/view_alert";
    }

    private String describeBackingEntity(LiveWireAlert alert) {
        final Group backingGroup = alert.getGroup() == null ? alert.getMeeting().getAncestorGroup() : alert.getGroup();
        final String groupName = backingGroup.getName();
        final int groupSize = backingGroup.getMemberships().size();
        return LiveWireAlertType.INSTANT.equals(alert.getType()) ?
                String.format("Flash news for group, %s, of size %d", groupName, groupSize) :
                String.format("Public gathering about '%s', of group, %s, with size %d", alert.getMeeting().getName(), groupName, groupSize);
    }

    // todo : introduce variation in file type etc once have videos being uploaded
    @RequestMapping(value = "media", method = RequestMethod.GET, produces = MediaType.IMAGE_JPEG_VALUE)
    @ResponseBody
    public FileSystemResource genImage(@RequestParam String imageKey) {
        MediaFileRecord record = storageBroker.retrieveMediaRecordsForFunction(MediaFunction.LIVEWIRE_MEDIA,
                Collections.singleton(imageKey)).iterator().next();
        return new FileSystemResource(storageBroker.fetchFileFromRecord(record));
    }

    @RequestMapping(value = "modify/headline", method = RequestMethod.POST)
    public String modifyHeadline(@RequestParam String alertUid, @RequestParam String headline,
                                 RedirectAttributes attributes, HttpServletRequest request) {
        liveWireAlertBroker.updateHeadline(getUserProfile().getUid(), alertUid, headline);
        addMessage(attributes, MessageType.SUCCESS, "livewire.alert.modified.done", request);
        attributes.addAttribute("alertUid", alertUid);
        return "redirect:/livewire/alert/view";
    }

    @RequestMapping(value = "modify/description", method = RequestMethod.POST)
    public String modifyDescription(@RequestParam String alertUid, @RequestParam String description,
                                    RedirectAttributes attributes, HttpServletRequest request) {
        liveWireAlertBroker.updateDescription(getUserProfile().getUid(), alertUid, description);
        addMessage(attributes, MessageType.SUCCESS, "livewire.alert.modified.done", request);
        attributes.addAttribute("alertUid", alertUid);
        return "redirect:/livewire/alert/view";
    }

    @RequestMapping(value = "modify/images/add", method = RequestMethod.POST)
    public @ResponseBody String addImage(@RequestParam String alertUid, @RequestParam MultipartFile image) {
        String mediaRecordUid = mediaFileBroker.storeFile(image, MediaFunction.LIVEWIRE_MEDIA, "image/jpeg", null); // means UID will be used for key
        liveWireAlertBroker.addMediaFile(getUserProfile().getUid(), alertUid, mediaFileBroker.load(mediaRecordUid));
        logger.info("added media file, returning UID = {}", mediaRecordUid);
        return mediaRecordUid;
    }

    @RequestMapping(value = "modify/images/delete", method = RequestMethod.POST)
    public String removeImage(@RequestParam String alertUid, @RequestParam String mediaFileUid,
                              RedirectAttributes attributes, HttpServletRequest request) {
        // todo : actually do this
        addMessage(attributes, MessageType.INFO, "livewire.alert.image.deleted", request);
        attributes.addAttribute("alertUid", alertUid);
        return "redirect:/livewire/alert/view";
    }

    @RequestMapping(value = "tag", method = RequestMethod.POST)
    public String tagAlert(@RequestParam String alertUid,
                           @RequestParam String tags,
                           RedirectAttributes attributes, HttpServletRequest request) {
        List<String> tagList = Arrays.asList(tags.split("\\s*,\\s*"));
        try {
            liveWireAlertBroker.setTagsForAlert(getUserProfile().getUid(), alertUid, tagList);
            addMessage(attributes, MessageType.SUCCESS, "livewire.tags.success", request);
        } catch (AccessDeniedException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.tags.denied", request);
        } catch (InvalidParameterException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.tags.invalid", request);
        }
        attributes.addAttribute("alertUid", alertUid);
        return "redirect:view";
    }

    @RequestMapping(value = "release", method = RequestMethod.POST)
    public String releaseAlert(@RequestParam String alertUid,
                               @RequestParam(required = false) String publicLists,
                               RedirectAttributes attributes, HttpServletRequest request) {
        try {
            List<String> listOfLists = publicLists != null ? Arrays.asList(publicLists.split(",")) : null;
            liveWireAlertBroker.reviewAlert(getUserProfile().getUid(), alertUid, null, true, listOfLists);
            LiveWireAlert alert = liveWireAlertBroker.load(alertUid);
            logger.info("alert lists? : {}", alert.getPublicListsUids());
            liveWireSendingBroker.sendLiveWireAlerts(Collections.singleton(alertUid));
            addMessage(attributes, MessageType.SUCCESS, "livewire.released.success", request);
        } catch (AccessDeniedException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.released.denied", request);
        } catch (IllegalArgumentException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.released.error", request);
        }
        attributes.addAttribute("alertUid", alertUid);
        return "redirect:view";
    }

    @RequestMapping(value = "block", method = RequestMethod.POST)
    public String blockAlert(@RequestParam String alertUid, RedirectAttributes attributes, HttpServletRequest request) {
        liveWireAlertBroker.reviewAlert(getUserProfile().getUid(), alertUid, null, false, null);
        addMessage(attributes, MessageType.INFO, "livewire.blocked.done", request);
        attributes.addAttribute("alertUid", alertUid);
        return "redirect:view";
    }
}
