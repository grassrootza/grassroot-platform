package za.org.grassroot.services.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.campaign.CampaignBroker;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfig.class)
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
@Transactional
public class CampaignBrokerTest {

    @Autowired
    private CampaignBroker campaignBroker;
    @Autowired
    private UserRepository userRepository;

    public void setUp(){

    }


    @Test
    public void testCreateCampaign(){
       // campaignBroker.createCampaign("national campaign","2345",)
    }

}
