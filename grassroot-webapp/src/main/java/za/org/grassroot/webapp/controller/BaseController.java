package za.org.grassroot.webapp.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class BaseController {

    public static final String MESSAGES_KEY = "successMessages";
    public static final String ERRORS_MESSAGES_KEY = "errors";

    public  enum  MessageType
    {
        INFO("infoMessage"),SUCCESS("successMessage"),ERROR("errorMessage");

        private String messageKey;

        MessageType(String messageKey)
        {
           this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private MessageSourceAccessor messageSourceAccessor;

    public String getMessage(String id) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(id, null, locale);
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


    public  void addMessage(Model model, MessageType messageType, String messageKey, HttpServletRequest request)
    {
        model.addAttribute(messageType.getMessageKey(), getText(messageKey, request.getLocale()));
    }

    public  void addMessage(RedirectAttributes redirectAttributes, MessageType messageType, String messageKey, HttpServletRequest request)
    {
        redirectAttributes.addFlashAttribute(messageType.getMessageKey(), getText(messageKey,request.getLocale()));
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
        return messageSourceAccessor.getMessage(msgKey, locale);
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
        return getText(msgKey, new Object[] { arg }, locale);
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
        return messageSourceAccessor.getMessage(msgKey, args, locale);
    }
}
