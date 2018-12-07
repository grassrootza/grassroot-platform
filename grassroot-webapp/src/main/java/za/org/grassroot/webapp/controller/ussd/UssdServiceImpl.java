package za.org.grassroot.webapp.controller.ussd;

import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.account.AccountFeaturesBroker;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.task.VoteBroker;
import za.org.grassroot.services.user.UserManager;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDMenuUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import static za.org.grassroot.webapp.enums.USSDSection.HOME;

@Service
public class UssdServiceImpl implements UssdService {
	private final Logger log = LoggerFactory.getLogger(UssdServiceImpl.class);

	@Override
	@Transactional
	public Request processStart(final String inputNumber, final String enteredUSSD) throws URISyntaxException {
		return null;
	}
}
