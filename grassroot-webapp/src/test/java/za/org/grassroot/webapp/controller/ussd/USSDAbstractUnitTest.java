package za.org.grassroot.webapp.controller.ussd;

import com.google.common.collect.Sets;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.handler.SimpleMappingExceptionResolver;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.integration.LearningService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.SafetyEventBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.livewire.DataSubscriberBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.services.livewire.LiveWireContactBroker;
import za.org.grassroot.services.task.*;
import za.org.grassroot.services.user.AddressBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.util.USSDGroupUtil;
import za.org.grassroot.webapp.util.USSDMenuUtil;

import java.time.LocalDate;
import java.time.Year;
import java.util.Properties;
import java.util.Set;

/**
 * Created by luke on 2015/11/20.
 */
@RunWith(MockitoJUnitRunner.class)
public abstract class USSDAbstractUnitTest {

    protected MockMvc mockMvc;

    @Mock
    protected UserManagementService userManagementServiceMock;

    @Mock
    protected GroupBroker groupBrokerMock;

    @Mock
    protected GroupRepository groupRepositoryMock;

    @Mock
    protected EventLogBroker eventLogBrokerMock;

    @Mock
    protected PermissionBroker permissionBrokerMock;

    @Mock
    protected EventBroker eventBrokerMock;

    @Mock
    protected LiveWireAlertBroker liveWireBrokerMock;

    @Mock
    protected LiveWireContactBroker liveWireContactBrokerMock;

    @Mock
    protected DataSubscriberBroker dataSubscriberBrokerMock;

    @Mock
    protected EventRequestBroker eventRequestBrokerMock;

    @Mock
    protected TodoBrokerNew todoBrokerMock;

    @Mock
    protected TodoRequestBroker todoRequestBrokerMock;

    @Mock
    protected CacheUtilService cacheUtilManagerMock;

    @Mock
    protected SafetyEventBroker safetyEventBrokerMock;

    @Mock
    protected AddressBroker addressBrokerMock;

    @Mock
    protected LearningService learningServiceMock;

    @Mock
    protected GroupQueryBroker groupQueryBrokerMock;

    @Mock
    protected AccountGroupBroker accountGroupBrokerMock;

    @InjectMocks
    protected USSDGroupUtil ussdGroupUtil;

    @InjectMocks
    protected USSDMenuUtil ussdMenuUtil;

    protected static final String base = "/ussd/";
    protected static final String userChoiceParam = "request";
    protected static final String interruptedChoice = "1";

    protected static final LocalDate testDay = LocalDate.of(Year.now().getValue(), 6, 16);
    protected static final Year testYear = testDay.isAfter(LocalDate.now()) ? Year.now() : Year.now().plusYears(1);

    protected HandlerExceptionResolver exceptionResolver() {
        SimpleMappingExceptionResolver exceptionResolver = new SimpleMappingExceptionResolver();
        Properties statusCodes = new Properties();

        statusCodes.put("error/404", "404");
        statusCodes.put("error/error", "500");

        exceptionResolver.setStatusCodes(statusCodes);
        return exceptionResolver;
    }

    protected LocalValidatorFactoryBean validator() {
        return new LocalValidatorFactoryBean();
    }

    protected ViewResolver viewResolver() {
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();

        // viewResolver.setViewClass
        viewResolver.setPrefix("/pages");
        viewResolver.setSuffix(".html");

        return viewResolver;
    }

    protected MessageSource messageSource() {
        // todo: wire this up to the actual message source so unit tests make sure languages don't have gaps ...
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();

        messageSource.setBasename("messages");
        messageSource.setUseCodeAsDefaultMessage(true);

        return messageSource;
    }

    protected void wireUpMessageSourceAndGroupUtil(USSDBaseController controller) {
        controller.setMessageSource(messageSource());
        ussdMenuUtil.setForTests(); // since inject mocks will not autowire
        ussdGroupUtil.setMessageSource(messageSource());
        controller.setUssdMenuUtil(ussdMenuUtil);
        controller.setUssdGroupUtil(ussdGroupUtil);
    }

    // helper method to generate a set of membership info ... used often
    protected Set<MembershipInfo> ordinaryMember(String phoneNumber) {
        return Sets.newHashSet(new MembershipInfo(phoneNumber, BaseRoles.ROLE_ORDINARY_MEMBER, null));
    }

    protected Set<MembershipInfo> organizer(User user) {
        return Sets.newHashSet(new MembershipInfo(user.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER, user.getDisplayName()));
    }
}
