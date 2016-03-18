package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.LogBookService;
import za.org.grassroot.webapp.model.rest.LogBookDTO;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by aakilomar on 9/5/15.
 */
@RestController
@RequestMapping(value = "/api/logbook")
public class LogBookRestController {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());


    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    LogBookService logBookService;




    @RequestMapping(value = "/add/{userid}/{groupid}/{message}", method = RequestMethod.POST)
    public LogBookDTO add(@PathVariable("userid") Long userid,
                          @PathVariable("groupid") Long groupid,
                          @PathVariable("message") String message) {
        return new LogBookDTO(logBookService.create(userid,groupid,message));
    }

    @RequestMapping(value = "/add/{userid}/{groupid}/{message}/{replicate}", method = RequestMethod.POST)
    public LogBookDTO add(@PathVariable("userid") Long userid,
                          @PathVariable("groupid") Long groupid,
                          @PathVariable("message") String message,
                          @PathVariable("replicate") boolean replicate) {
        return new LogBookDTO(logBookService.create(userid,groupid,message,replicate));
    }

    @RequestMapping(value = "/addwithdate/{userid}/{groupid}/{message}/{actionByDate}", method = RequestMethod.POST)
    public LogBookDTO addWithDate(@PathVariable("userid") Long userid,
                          @PathVariable("groupid") Long groupid,
                          @PathVariable("message") String message,
                          @PathVariable("actionByDate") @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss") Date actionByDate) {
        return new LogBookDTO(logBookService.create(userid,groupid,message,new Timestamp(actionByDate.getTime())));
    }

    @RequestMapping(value = "/addwithdateandassign/{userid}/{groupid}/{message}/{actionByDate}/{assignedToUserId}", method = RequestMethod.POST)
    public LogBookDTO addWithDateAndAssign(@PathVariable("userid") Long userid,
                                  @PathVariable("groupid") Long groupid,
                                  @PathVariable("message") String message,
                                  @PathVariable("actionByDate") @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss") Date actionByDate,
                                  @PathVariable("assignedToUserId") Long assignedToUserId) {
        return new LogBookDTO(logBookService.create(userid,groupid,message,new Timestamp(actionByDate.getTime()),assignedToUserId));
    }

    @RequestMapping(value = "/listreplicated/{groupid}", method = RequestMethod.GET)
    public List<LogBookDTO> list_replicated(@PathVariable("groupid") Long groupid) {
        List<LogBookDTO> list = new ArrayList<>();
        List<LogBook> replicated = logBookService.getAllReplicatedEntriesForGroup(groupid);
        for (LogBook l : replicated) {
            list.add(new LogBookDTO(l));
        }
        return list;
    }

    @RequestMapping(value = "/listreplicated/{groupid}/{completed}", method = RequestMethod.GET)
    public List<LogBookDTO> list_replicated(@PathVariable("groupid") Long groupid,
                                            @PathVariable("completed") boolean completed) {
        List<LogBookDTO> list = new ArrayList<>();
        List<LogBook> replicated = logBookService.getAllReplicatedEntriesForGroup(groupid,completed);
        for (LogBook l : replicated) {
            list.add(new LogBookDTO(l));
        }
        return list;
    }

    @RequestMapping(value = "/listreplicatedbymessage/{groupid}/{message}", method = RequestMethod.GET)
    public List<LogBookDTO> list_replicated(@PathVariable("groupid") Long groupid,
                                            @PathVariable("message") String message) {
        List<LogBookDTO> list = new ArrayList<>();
        List<LogBook> replicated = logBookService.getAllReplicatedEntriesForGroupAndMessage(groupid,message);
        for (LogBook l : replicated) {
            list.add(new LogBookDTO(l));
        }
        return list;
    }


}
