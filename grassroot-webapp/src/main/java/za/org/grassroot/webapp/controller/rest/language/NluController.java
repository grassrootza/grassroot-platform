package za.org.grassroot.webapp.controller.rest.language;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.integration.LearningService;

import java.time.ZoneOffset;

@Slf4j
@RestController
@Api("/api/language/parse")
@RequestMapping(value = "/api/language/parse")
public class NluController {

    private final LearningService learningService;

    @Autowired
    public NluController(LearningService learningService) {
        this.learningService = learningService;
    }

    @RequestMapping(value = "/datetime/text", method = RequestMethod.GET)
    @ApiOperation(value = "Parse a text string for a date and time", notes = "Parses a date time and returns an epoch milli " +
            "timestamp if parsing is successful, or an error if not")
    public ResponseEntity<Long> parseDateTime(@RequestParam String text) {
        return ResponseEntity.ok(learningService.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli());
    }

}
