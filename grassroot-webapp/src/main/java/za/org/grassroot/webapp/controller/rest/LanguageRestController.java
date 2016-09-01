package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.integration.domain.SeloParseDateTimeFailure;
import za.org.grassroot.integration.services.LearningService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Created by luke on 2016/08/05.
 */
@RestController
@RequestMapping(value = "/api/language")
public class LanguageRestController {

	private static final Logger logger = LoggerFactory.getLogger(LanguageRestController.class);

	@Autowired
	private LearningService learningService;

	// todo : switch path mapping to selo
	@RequestMapping("/test/natty")
	public ResponseEntity<ResponseWrapper> getNattyInterpretation(@RequestParam String inputString) {
		try {
			LocalDateTime dateTime = learningService.parse(inputString);
			logger.info("got as input = {}, parsed as = {}", inputString, dateTime.format(DateTimeFormatter.ISO_DATE_TIME));
			return RestUtil.okayResponseWithData(RestMessage.DATE_TIME_PARSED, dateTime);
		} catch (SeloParseDateTimeFailure e) {
			return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.DATE_TIME_FAILED);
		}
	}

}
