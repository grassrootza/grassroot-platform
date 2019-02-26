package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.integration.LearningService;
import za.org.grassroot.integration.experiments.ExperimentBroker;
import za.org.grassroot.integration.location.LocationInfoBroker;
import za.org.grassroot.integration.location.UssdLocationServicesBroker;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.SafetyEventBroker;
import za.org.grassroot.services.UserResponseBroker;
import za.org.grassroot.services.account.AccountFeaturesBroker;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.geo.AddressBroker;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupJoinRequestService;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.group.MemberDataExportBroker;
import za.org.grassroot.services.livewire.DataSubscriberBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.services.livewire.LiveWireContactBroker;
import za.org.grassroot.services.task.*;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.util.USSDEventUtil;
import za.org.grassroot.webapp.util.USSDGroupUtil;
import za.org.grassroot.webapp.util.USSDMenuUtil;

import java.util.Arrays;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public abstract class UssdUnitTest {
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
	protected GeoLocationBroker geoLocationBrokerMock;

	@Mock
	protected UssdLocationServicesBroker ussdLocationServicesBrokerMock;

	@Mock
	protected LiveWireAlertBroker liveWireBrokerMock;

	@Mock
	protected LiveWireContactBroker liveWireContactBrokerMock;

	@Mock
	protected DataSubscriberBroker dataSubscriberBrokerMock;

	@Mock
	protected EventRequestBroker eventRequestBrokerMock;

	@Mock
	protected LocationInfoBroker locationInfoBrokerMock;

	@Mock
	protected TodoBroker todoBrokerMock;

	@Mock
 	protected ExperimentBroker experimentBrokerMock;

	@Mock
	protected TaskBroker taskBrokerMock;

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

	@Mock
	protected VoteBroker voteBrokerMock;

	@Mock
	protected GroupJoinRequestService groupJoinRequestServiceMock;

	protected USSDEventUtil ussdEventUtil;
	protected USSDGroupUtil ussdGroupUtil;
	protected UssdSupport ussdSupport;
	protected USSDMessageAssembler ussdMessageAssembler;

	protected final static List<User> languageUsers = constructLanguageUsers();

	private static List<User> constructLanguageUsers() {
		/* We use these quite often */
		String baseForOthers = "2781000111";
		User testUserZu = new User(baseForOthers + "2", null, null);
		User testUserTs = new User(baseForOthers + "3", null, null);
		User testUserNso = new User(baseForOthers + "4", null, null);
		User testUserSt = new User(baseForOthers + "5", null, null);

		testUserZu.setLanguageCode("zu");
		testUserTs.setLanguageCode("ts");
		testUserNso.setLanguageCode("nso");
		testUserSt.setLanguageCode("st");

		return Arrays.asList(testUserNso, testUserSt, testUserTs, testUserZu);
	}

	@Before
	public void parentSetUp() {
		final MessageSource messageSource = newMessageSource();
		final USSDMenuUtil ussdMenuUtil = new USSDMenuUtil("http://127.0.0.1:8080/ussd/", 140, 160);

		this.ussdMessageAssembler = new USSDMessageAssembler(messageSource);
		this.ussdSupport = new UssdSupport(experimentBrokerMock, userManagementServiceMock, ussdMessageAssembler, ussdMenuUtil);
		this.ussdEventUtil = new USSDEventUtil(messageSource, eventBrokerMock, eventRequestBrokerMock, userLoggerMock, learningServiceMock);
		this.ussdGroupUtil = new USSDGroupUtil(messageSource, groupBrokerMock, groupQueryBrokerMock, permissionBrokerMock, groupJoinRequestServiceMock);
	}

	private static MessageSource newMessageSource() {
		ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();

		messageSource.setBasename("messages");
		messageSource.setUseCodeAsDefaultMessage(true);

		return messageSource;
	}

}
