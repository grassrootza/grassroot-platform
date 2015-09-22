package za.org.grassroot.services;

import org.springframework.context.support.AbstractMessageSource;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * @author Lesetse Kimwaga
 */
public class GrassRootServicesMessageSource extends AbstractMessageSource {
    @Override
    protected MessageFormat resolveCode(String code, Locale locale) {
        return null;
    }
}
