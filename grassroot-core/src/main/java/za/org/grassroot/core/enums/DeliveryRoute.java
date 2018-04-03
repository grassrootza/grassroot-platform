package za.org.grassroot.core.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Created by luke on 2016/04/02.
 * Enum to help distinguish between whether a user prefers our-app notifcations (Android), or SMS, or -- later -- other
 * types of messages as their default. This may also be used for recording messages send and so forth.
 *
 */
public enum DeliveryRoute {

    SHORT_MESSAGE, // will try GCM or WhatsApp before SMS
    SMS, // forces SMS itself
    WHATSAPP, // so that it is in
    ANDROID_APP, // i.e., push notifications within the Grassroot App
    EMAIL_GRASSROOT, // though note we will have to work out how to put in place threshold limits for broadcasts if no MailChimp
    EMAIL_USERACCOUNT, // if they want the email to come from the address they have on file
    EMAIL_3RDPARTY, // if the email will come from eg a MailChimp account
    WEB_ONLY; // for security/privacy-conscious users who do not want the SMSs and do not have Android

    public static final List<DeliveryRoute> EMAIL_ROUTES = Arrays.asList(EMAIL_3RDPARTY, EMAIL_GRASSROOT, EMAIL_USERACCOUNT);

    public static final List<DeliveryRoute> TEXT_ROUTES = Arrays.asList(SHORT_MESSAGE, SMS, WHATSAPP, ANDROID_APP);

}
