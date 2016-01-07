package za.org.grassroot.webapp.controller.webapp;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;
import org.powermock.api.mockito.PowerMockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;



import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import javax.annotation.Resource;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import org.junit.runner.RunWith;
import org.springframework.security.web.debug.DebugFilter;



/*
 * Paballo Ditshego 06/01/2016
 */




public class MeetingControllerTest extends WebAppAbstractUnitTest {

	private static final Logger log = LoggerFactory.getLogger(MeetingControllerTest.class);


	
	@Autowired 
	private FilterChainProxy springSecurityFilterChain; 
	
	@Autowired
	protected WebApplicationContext wac;

	

	@Before
	public void setUp() throws Exception {
		
		

		mockMvc = MockMvcBuilders.
			    webAppContextSetup(wac).addFilters(springSecurityFilterChain)
			  .apply(springSecurity()).build();
				

	

	}

	/*
	 * @Test public void shouldShowMeetingDetails() throws Exception {
	 * 
	 * // will have to figure out a way to do it cleanly long eventId = 1L;
	 * Event dummyMeeting = new Event(); dummyMeeting.setId(eventId);
	 * 
	 * User dummyUser = new User("", "testUser"); dummyUser.setId(1L);
	 * List<User> listOfDummyYesResponses = mock(List.class); Map<User,
	 * EventRSVPResponse> dummyRsvpResponses = mock(Map.class);
	 * 
	 * when(eventManagementServiceMock.loadEvent(eventId)).thenReturn(
	 * dummyMeeting);
	 * when(eventManagementServiceMock.getListOfUsersThatRSVPYesForEvent(
	 * dummyMeeting)) .thenReturn(listOfDummyYesResponses);
	 * when(eventManagementServiceMock.getRSVPResponses(dummyMeeting)).
	 * thenReturn(dummyRsvpResponses);
	 * when(listOfDummyYesResponses.size()).thenReturn(1);
	 * 
	 * mockMvc.perform(get("/meeting/view").param("eventId",
	 * String.valueOf(eventId))).andExpect(status().isOk())
	 * .andExpect(view().name("meeting/view"))
	 * .andExpect(model().attribute("meeting", hasProperty("id", is(1L))))
	 * .andExpect(model().attribute("rsvpYesTotal", equalTo(1))); //
	 * .andExpect(model().attribute("rsvpResponses",hasEntry("dummyUser",
	 * EventRSVPResponse.YES)));
	 * 
	 * verify(eventManagementServiceMock, times(1)).loadEvent(eventId); //
	 * verify(eventManagementServiceMock, //
	 * times(1)).getListOfUsersThatRSVPYesForEvent(dummyMeeting); //
	 * verify(eventManagementServiceMock, //
	 * times(1)).getRSVPResponses(dummyMeeting);
	 * 
	 * }
	 * 
	 * /*
	 * 
	 * @Test public void TestCreateMeetingWorks() throws Exception{
	 * 
	 * Event dummyMeeting = new Event();
	 * 
	 * long selectedGroupId = 1L; long time = System.currentTimeMillis();
	 * 
	 * dummyMeeting.setEventStartDateTime(new Timestamp(time));
	 * dummyMeeting.setDateTimeString(new SimpleDateFormat("E d MMM HH:mm")
	 * .format(dummyMeeting.getEventStartDateTime())); dummyMeeting.setId(1L);
	 * 
	 * 
	 * when(eventManagementServiceMock.updateEvent(dummyMeeting)).thenReturn(
	 * dummyMeeting); when(eventManagementServiceMock.setGroup(dummyMeeting,
	 * selectedGroupId)).thenReturn(dummyMeeting);
	 * 
	 * mockMvc.perform(post("/meeting/create").param("selectedGroupId",
	 * String.valueOf(selectedGroupId))).andExpect(status().isOk())
	 * .andExpect(view().name("redirect:/home"))
	 * .andExpect(redirectedUrl("/home"));
	 * 
	 * 
	 * 
	 * }
	 */

	@Test
	public void TestSendFreeFormWorksWithGroupSpecified() throws Exception {

		Long groupId = 1L;
		

		User testUser = new User("", "testuser");
		Group testGroup = new Group("", testUser);
		testGroup.setId(groupId);		
		
		
		
		when(groupManagementServiceMock.loadGroup(groupId)).thenReturn(testGroup);
		

		mockMvc.perform(get("/meeting/free").with(user("")).param("groupId", String.valueOf(groupId)))
				.andExpect(view().name("meeting/free"))
				.andExpect(model().attribute("group", hasProperty("id", is(1L))));
		// .andExpect(model().attribute("groupSpecified", true));

		verify(groupManagementServiceMock, times(1)).loadGroup(groupId);
		verifyNoMoreInteractions(groupManagementServiceMock);

	}
	
	
}



/*
 * @Test
 * 
 * public void TestSendFreeFormWorksWithGroupNotSpecified() throws Exception {
 * 
 * // Boolean groupSpecified = mock(Boolean.class);
 * 
 * User testUser = new User("", "testUser"); List<Group> dummyGroups =
 * mock(ArrayList.class);
 * 
 * when(groupManagementServiceMock.getGroupsPartOf(testUser)).thenReturn(
 * dummyGroups); // when(groupSpecified).thenReturn(false);
 * 
 * mockMvc.perform(get("/meeting/free")).andExpect((view().name("/meeting/fre"))
 * ) .andExpect(model().attribute("userGroups", dummyGroups)); //
 * .andExpect(model().attribute("groupSpecified", false));
 * 
 * verify(groupManagementServiceMock, times(1)).getGroupsPartOf(testUser);
 * verifyNoMoreInteractions(groupManagementServiceMock);
 * 
 * }
 * 
 * @Test public void rsvpNoShouldWork() throws Exception {
 * 
 * }
 * 
 * @Test public void rsvpYesShouldWork() throws Exception {
 * 
 * }
 * 
 * @Test public void sendRemiderShoudWork() {
 * 
 * }
 * 
 * }
 */

// }
