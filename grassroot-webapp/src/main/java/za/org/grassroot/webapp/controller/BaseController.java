package za.org.grassroot.webapp.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.util.LocalDateTimePropertyEditor;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@Controller
public class BaseController {

    private static final Logger log = LoggerFactory.getLogger(BaseController.class);
    private static final String SNACKBAR_MESSAGE = "snackbarMessage";
    protected enum SourceMarker {
        HOME,
        GROUP
    }
    public enum MessageType {
        INFO("infoMessage"),
        SUCCESS("successMessage"),
        ERROR("errorMessage");

        private String messageKey;
        MessageType(String messageKey) {
            this.messageKey = messageKey;
        }
        public String getMessageKey() {
            return messageKey;
        }
    }

    // Restriction levels
    public final static int PRIVATE_LEVEL = 0;
    public final static int PUBLIC_LEVEL = 1;
    public final static int ALL_LEVEL = 2;

    @Autowired
    @Qualifier("messageSource")
    private MessageSource messageSource;

    @Autowired
    @Qualifier("messageSourceAccessor")
    private MessageSourceAccessor messageSourceAccessor;

    @Autowired
    protected UserManagementService userManagementService;

    @Autowired
    protected PermissionBroker permissionBroker;

    @InitBinder
    protected void initBinder(HttpServletRequest request, ServletRequestDataBinder binder) {
        binder.registerCustomEditor(java.time.LocalDateTime.class, new LocalDateTimePropertyEditor());
    }

    public static Map<String, String> getImplementedLanguages() {
        final LinkedHashMap<String, String> languages = new LinkedHashMap<>();

        languages.put("en", "English");
        languages.put("nso", "Sepedi");
        languages.put("st", "Sesotho");
        languages.put("ts", "Tsonga");
        languages.put("zu", "Zulu");
        languages.put("af", "Afrikaans");

        return languages;
    }

    protected User getUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.debug("BaseController: authentication properties ... authenticated: " + authentication.isAuthenticated() + " ... and principal ... " + authentication.getPrincipal());
        if (authentication.isAuthenticated() && authentication.getPrincipal() != null) {
            return (User) authentication.getPrincipal();
        }
        throw new AuthenticationServiceException("Invalid logged in user profile");
    }

    // for when user finishes account sign up, to force addition of account role for the user
    protected void refreshAuthorities() {
        final String userUid = getUserProfile().getUid();
        User updatedUser = userManagementService.load(userUid);
        Authentication authentication = new UsernamePasswordAuthenticationToken(updatedUser, null, updatedUser.getAuthorities());
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(authentication);
    }

    /* Helper method used in all the meeting/vote/logbook fields */
    public static List<String[]> reminderMinuteOptions(boolean includeZero) {

        List<String[]> minuteOptions = new ArrayList<>();

        String[] twoDays = new String[]{"" + 48 * 60, "Two days ahead"};
        String[] oneDay = new String[]{"" + 24 * 60, "One day ahead"};
        String[] halfDay = new String[]{"" + 6 * 60, "Half a day ahead"};
        String[] oneHour = new String[]{"60", "An hour before"};
        String[] zero = new String[]{"0", "No reminder"};

        minuteOptions.add(twoDays);
        minuteOptions.add(oneDay);
        minuteOptions.add(halfDay);
        minuteOptions.add(oneHour);
        if (includeZero) minuteOptions.add(zero);

        return minuteOptions;
    }

    //Methods to help with standalone unit testing

    public void setMessageSource(MessageSource messageSource){
        this.messageSource = messageSource;
        this.messageSourceAccessor = new MessageSourceAccessor(messageSource);

    }

    public void addMessage(Model model, MessageType messageType, String messageKey, HttpServletRequest request) {
        model.addAttribute(messageType.getMessageKey(), getText(messageKey, request.getLocale()));
        model.addAttribute(SNACKBAR_MESSAGE, getText(messageKey, request.getLocale()));
    }

    public void addMessage(Model model, MessageType messageType, String messageKey, Object[] arguments, HttpServletRequest request) {
        model.addAttribute(messageType.getMessageKey(), getText(messageKey, arguments, request.getLocale()));
        model.addAttribute(SNACKBAR_MESSAGE, getText(messageKey, arguments, request.getLocale()));
    }

    public void addMessage(RedirectAttributes redirectAttributes, MessageType messageType, String messageKey, HttpServletRequest request) {
        redirectAttributes.addFlashAttribute(messageType.getMessageKey(), getText(messageKey, request.getLocale()));
        redirectAttributes.addFlashAttribute(SNACKBAR_MESSAGE, getText(messageKey, request.getLocale()));
    }

    public void addMessage(RedirectAttributes redirectAttributes, MessageType messageType, String messageKey,Object [] arguments, HttpServletRequest request) {
        redirectAttributes.addFlashAttribute(messageType.getMessageKey(), getText(messageKey, arguments, request.getLocale()));
        redirectAttributes.addFlashAttribute(SNACKBAR_MESSAGE, getText(messageKey, arguments, request.getLocale()));
    }

    /**
     * Convenience method for getting a i18n key's value.  Calling
     * getMessageSourceAccessor() is used because the RequestContext variable
     * is not set in unit tests b/c there's no DispatchServlet Request.
      */
    private String getText(String msgKey, Locale locale) {
        return messageSourceAccessor.getMessage("web." + msgKey, locale);
    }

    protected String getMessage(String msgKey) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage("web." + msgKey, null, locale);
    }

    /**
     * Convenient method for getting a i18n key's value with a single
     * string argument.
     */
    protected String getMessage(String msgKey, String arg) {
        return getText(msgKey, new Object[]{arg}, LocaleContextHolder.getLocale());
    }

    /**
     * Convenience method for getting a i18n key's value with arguments.
     * */
    private String getText(String msgKey, Object[] args, Locale locale) {
        return messageSourceAccessor.getMessage("web." + msgKey, args, locale);
    }
}
