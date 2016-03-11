package za.org.grassroot.webapp.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.GroupAccessControlManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.util.SqlTimestampPropertyEditor;

import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class BaseController {

    private static final Logger log = LoggerFactory.getLogger(BaseController.class);

    public static final String MESSAGES_KEY        = "successMessages";
    public static final String ERRORS_MESSAGES_KEY = "errors";

    public enum MessageType {
        INFO("infoMessage"), SUCCESS("successMessage"), ERROR("errorMessage");

        private String messageKey;

        MessageType(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }

    @Autowired
    @Qualifier("messageSource")
    private MessageSource messageSource;

    @Autowired
    @Qualifier("messageSourceAccessor")
    protected MessageSourceAccessor messageSourceAccessor; // making it protected just briefly, for SMS send

    @Autowired
    protected UserManagementService userManagementService;

    @Autowired
    protected GroupAccessControlManagementService groupAccessControlManagementService;

    @Autowired
    protected GroupManagementService groupManagementService;

    @Autowired
    protected PermissionBroker permissionBroker;


    protected User getUserProfile() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("BaseController: authentication properties ... authenticated: " + authentication.isAuthenticated() + " ... and principal ... " + authentication.getPrincipal());
        if (authentication.isAuthenticated() && authentication.getPrincipal() != null) {
            return (User) authentication.getPrincipal();
        }
        throw new AuthenticationServiceException("Invalid logged in user profile");
    }

    /* protected User getUserProfile() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userManagementService.fetchUserByUsername(authentication.getName());
    }*/

    public Group loadAuthorizedGroup(Long groupId, Permission permission) {
        Group group = groupManagementService.loadGroup(groupId);
        if (group == null) {
            throw new IllegalArgumentException("Group '" + groupId + "' does not exist.");
        }

        User user = getUserProfile();
        if (!permissionBroker.isGroupPermissionAvailable(user, group, permission)) {
            throw new AccessDeniedException("Unauthorised access '" + permission.getAuthority() + "' for Group '" + group.getGroupName() + "'");
        }
        return group;
//        return  groupAccessControlManagementService.loadAuthorizedGroup(groupId,permission);
    }

    public String getMessage(String id) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage("web." + id, null, locale);
    }


    @SuppressWarnings("unchecked")
    public void saveError(HttpServletRequest request, String error) {
        List errors = (List) request.getSession().getAttribute(ERRORS_MESSAGES_KEY);
        if (errors == null) {
            errors = new ArrayList();
        }
        errors.add(error);
        request.getSession().setAttribute(ERRORS_MESSAGES_KEY, errors);
    }

    @SuppressWarnings("unchecked")
    public void saveMessage(HttpServletRequest request, String msg) {
        List messages = (List) request.getSession().getAttribute(MESSAGES_KEY);

        if (messages == null) {
            messages = new ArrayList();
        }

        messages.add(msg);
        request.getSession().setAttribute(MESSAGES_KEY, messages);
    }

    //Methods to help with standalone unit testing

    public void setMessageSource(MessageSource messageSource){
        this.messageSource = messageSource;
        this.messageSourceAccessor = new MessageSourceAccessor(messageSource);

    }




    /**
     *
     * @param model
     * @param messageType
     * @param messageKey
     * @param request
     */
    public void addMessage(Model model, MessageType messageType, String messageKey, HttpServletRequest request) {
        model.addAttribute(messageType.getMessageKey(), getText(messageKey, request.getLocale()));
    }

    public void addMessage(Model model, MessageType messageType, String messageKey, Object[] arguments, HttpServletRequest request) {
        model.addAttribute(messageType.getMessageKey(), getText(messageKey, arguments, request.getLocale()));
    }

    /**
     *
     * @param redirectAttributes
     * @param messageType
     * @param messageKey
     * @param request
     */
    public void addMessage(RedirectAttributes redirectAttributes, MessageType messageType, String messageKey, HttpServletRequest request) {
        redirectAttributes.addFlashAttribute(messageType.getMessageKey(), getText(messageKey, request.getLocale()));
    }

    /**
     *
     * @param redirectAttributes
     * @param messageType
     * @param messageKey
     * @param arguments
     * @param request
     */
    public void addMessage(RedirectAttributes redirectAttributes, MessageType messageType, String messageKey,Object [] arguments, HttpServletRequest request) {
        redirectAttributes.addFlashAttribute(messageType.getMessageKey(), getText(messageKey, arguments, request.getLocale()));
    }


    /**
     * Convenience method for getting a i18n key's value.  Calling
     * getMessageSourceAccessor() is used because the RequestContext variable
     * is not set in unit tests b/c there's no DispatchServlet Request.
     *
     * @param msgKey
     * @param locale the current locale
     * @return
     */
    public String getText(String msgKey, Locale locale) {
        return messageSourceAccessor.getMessage("web." + msgKey, locale);
    }

    /**
     * Convenient method for getting a i18n key's value with a single
     * string argument.
     *
     * @param msgKey
     * @param arg
     * @param locale the current locale
     * @return
     */
    public String getText(String msgKey, String arg, Locale locale) {
        return getText("web." + msgKey, new Object[]{arg}, locale);
    }

    /**
     * Convenience method for getting a i18n key's value with arguments.
     *
     * @param msgKey
     * @param args
     * @param locale the current locale
     * @return
     */
    public String getText(String msgKey, Object[] args, Locale locale) {
        return messageSourceAccessor.getMessage("web." + msgKey, args, locale);
    }

    @InitBinder
    protected void initBinder(HttpServletRequest request, ServletRequestDataBinder binder) {
        binder.registerCustomEditor(java.sql.Timestamp.class, new SqlTimestampPropertyEditor());
    }
}
