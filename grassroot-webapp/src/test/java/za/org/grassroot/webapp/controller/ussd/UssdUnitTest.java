package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.integration.LearningService;
import za.org.grassroot.integration.location.LocationInfoBroker;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.SafetyEventBroker;
import za.org.grassroot.services.UserResponseBroker;
import za.org.grassroot.services.account.AccountFeaturesBroker;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.geo.AddressBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.group.MemberDataExportBroker;
import za.org.grassroot.services.livewire.DataSubscriberBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.services.livewire.LiveWireContactBroker;
import za.org.grassroot.services.task.*;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.util.USSDMenuUtil;

@RunWith(MockitoJUnitRunner.class)
public class UssdUnitTest {
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
	protected UserResponseBroker userResponseBrokerMock;

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
	protected LocationInfoBroker locationInfoBroker;

	@Mock
	protected TodoBroker todoBrokerMock;

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
	protected MemberDataExportBroker memberDataExportBrokerMock;

	@Mock
	protected AccountFeaturesBroker accountFeaturesBrokerMock;

	@Mock
	protected CampaignBroker campaignBrokerMock;

	@Mock
	protected AsyncUserLogger userLoggerMock;

	protected UssdSupport ussdSupport;

	@Before
	public void parentSetUp() {
		final USSDMessageAssembler ussdMessageAssembler = new USSDMessageAssembler(newMessageSource());
		final USSDMenuUtil ussdMenuUtil = new USSDMenuUtil("http://127.0.0.1:8080/ussd/", 140, 160);
		ussdSupport = new UssdSupport(null, userManagementServiceMock, ussdMessageAssembler, ussdMenuUtil);
	}

	private static MessageSource newMessageSource() {
		ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();

		messageSource.setBasename("messages");
		messageSource.setUseCodeAsDefaultMessage(true);

		return messageSource;
	}

}
