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
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.integration.LearningService;
import za.org.grassroot.integration.language.NluBroker;
import za.org.grassroot.integration.language.NluParseResult;

import java.time.ZoneOffset;

@Slf4j
@RestController
@Api("/api/language/parse")
@RequestMapping(value = "/api/language/parse")
public class NluController {

    private final LearningService learningService;
    private final NluBroker nluBroker;

    @Autowired
    public NluController(LearningService learningService, NluBroker nluBroker) {
        this.learningService = learningService;
        this.nluBroker = nluBroker;
    }

    @RequestMapping(value = "/datetime/text", method = RequestMethod.GET)
    @ApiOperation(value = "Parse a text string for a date and time", notes = "Parses a date time and returns an epoch milli " +
            "timestamp if parsing is successful, or an error if not")
    public ResponseEntity<Long> parseDateTime(@RequestParam String text) {
        return ResponseEntity.ok(learningService.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli());
    }

    @RequestMapping(value = "/intent", method = RequestMethod.GET)
    @ApiOperation(value = "Parse a string for intents and entities")
    public ResponseEntity<NluParseResult> parseFreeText(@RequestParam String text,
                                                        @RequestParam(required = false) String conversationUid) {
        return ResponseEntity.ok(nluBroker.parseText(text, conversationUid));
    }


    @RequestMapping(value = "/speech", method = RequestMethod.POST)
    @ApiOperation(value = "Convert speech to text, optionally parsing for entities")
    public ResponseEntity parseSpeech(@RequestParam(required = false) String encoding,
                                      @RequestParam int sampleRate,
                                      @RequestParam MultipartFile file,
                                      @RequestParam boolean parseForIntent) {
        return parseForIntent ?
                ResponseEntity.ok(nluBroker.speechToIntent(file, encoding, sampleRate)) :
                ResponseEntity.ok(nluBroker.speechToText(file, encoding, sampleRate));
    }

}
