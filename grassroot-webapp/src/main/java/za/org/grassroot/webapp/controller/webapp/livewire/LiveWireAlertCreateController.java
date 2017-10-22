package za.org.grassroot.webapp.controller.webapp.livewire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.livewire.DataSubscriber;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.enums.LiveWireAlertDestType;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.services.livewire.DataSubscriberBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.UidNameDTO;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/livewire/alert/create")
public class LiveWireAlertCreateController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(LiveWireAlertCreateController.class);

    @Value("${grassroot.livewire.instant.minsize:100}")
    private int minGroupSizeForInstantAlert;

    @Value("${grassroot.livewire.instant.mintasks:5}")
    private int minGroupTasksForInstantAlert;

    private static final String CONTACT_SELF = "self";
    private static final String CONTACT_OTHER = "other";

    private final LiveWireAlertBroker liveWireAlertBroker;
    private final DataSubscriberBroker dataSubscriberBroker;
    private final MediaFileBroker mediaFileBroker;

    @Autowired
    public LiveWireAlertCreateController(LiveWireAlertBroker liveWireAlertBroker, DataSubscriberBroker dataSubscriberBroker, MediaFileBroker mediaFileBroker) {
        this.liveWireAlertBroker = liveWireAlertBroker;
        this.dataSubscriberBroker = dataSubscriberBroker;
        this.mediaFileBroker = mediaFileBroker;
    }

    @RequestMapping(value = {"", "/"})
    public String createAlertForm(Model model) {
        final String userUid = getUserProfile().getUid();
        if (liveWireAlertBroker.canUserCreateAlert(userUid)) {
            Map<String, String> contactOptions = new LinkedHashMap<>();
            contactOptions.put(CONTACT_SELF, "Me");
            contactOptions.put(CONTACT_OTHER, "Someone else");
            model.addAttribute("contactOptions", contactOptions);
            model.addAttribute("userHasList", dataSubscriberBroker.doesUserHaveCustomLiveWireList(userUid));
            model.addAttribute("hasGroups", liveWireAlertBroker.countGroupsForInstantAlert(userUid) > 0);
            model.addAttribute("hasMeetings", !liveWireAlertBroker.meetingsForAlert(userUid).isEmpty());
            return "livewire/create";
        } else {
            model.addAttribute("minSize", minGroupSizeForInstantAlert);
            model.addAttribute("minTasks", minGroupTasksForInstantAlert);
            return "livewire/denied";
        }
    }

    @RequestMapping("/groups")
    public @ResponseBody List<UidNameDTO> getPossibleGroups() {
        return liveWireAlertBroker.groupsForInstantAlert(getUserProfile().getUid(), null, null)
                .stream().map(UidNameDTO::new).collect(Collectors.toList());
    }

    @RequestMapping("/meetings")
    public @ResponseBody List<UidNameDTO> getPossibleMeetings() {
        return liveWireAlertBroker.meetingsForAlert(getUserProfile().getUid())
                .stream().map(UidNameDTO::new).collect(Collectors.toList());
    }

    // jQuery file upload handles JSON better, so using that
    @RequestMapping("/image/add")
    public @ResponseBody UidNameDTO uploadImage(@RequestParam MultipartFile image) {
        // todo: proper MIME type handling
        final String mediaFileUid = mediaFileBroker.storeFile(image, MediaFunction.LIVEWIRE_MEDIA, "image/jpeg", null);
        logger.info("stored image, returning UID = {}", mediaFileUid);
        return new UidNameDTO(mediaFileUid, "");
    }

    @RequestMapping(value = "/submit", method = RequestMethod.POST)
    public String createLiveWireAlert(@RequestParam String headline,
                                      @RequestParam(value = "contact_person_type") String contactType,
                                      @RequestParam(value = "contact_name", required = false) String contactName,
                                      @RequestParam(value = "contact_number", required = false) String contactNumber,
                                      @RequestParam(value = "type") LiveWireAlertType type,
                                      @RequestParam(value = "group", required = false) String groupUid,
                                      @RequestParam(value = "meeting", required = false) String meetingUid,
                                      @RequestParam(value = "imageUid", required = false) String imageUid,
                                      @RequestParam(value = "destination", required = false) LiveWireAlertDestType destination,
                                      @RequestParam(value = "description", required = false) String description,
                                      RedirectAttributes attributes, HttpServletRequest request) {
        try {
            logger.info("contact person type = {}, name = {}, number = {}", contactType, contactName, contactNumber);

            final String contactUserUid = CONTACT_SELF.equals(contactType) ? null :
                    userManagementService.loadOrCreateUser(PhoneNumberUtil.convertPhoneNumber(contactNumber)).getUid();

            logger.info("contact user = {}", userManagementService.load(contactUserUid));

            final DataSubscriber dataSubscriber = (destination != null && !LiveWireAlertDestType.PUBLIC_LIST.equals(destination)) ?
                    dataSubscriberBroker.fetchLiveWireListForSubscriber(getUserProfile().getUid()) : null;

            logger.info("imageUid = {}", imageUid);
            List<MediaFileRecord> mediaRecords = StringUtils.isEmpty(imageUid) ? null :
                    Collections.singletonList(mediaFileBroker.load(imageUid));

            liveWireAlertBroker.createAsComplete(getUserProfile().getUid(), headline, description, type,
                    type.equals(LiveWireAlertType.INSTANT) ? groupUid : meetingUid,
                    contactUserUid, contactName, null, destination, dataSubscriber, mediaRecords);

            addMessage(attributes, MessageType.SUCCESS, "livewire.alert.submitted.done", request);
            return "redirect:/home";
        } catch (InvalidPhoneNumberException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.alert.submitted.phone", request);
            return "redirect:/livewire/alert/create";
        } catch (IllegalArgumentException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.alert.submitted.error", request);
            return "redirect:/livewire/alert/create";
        }
    }

}
