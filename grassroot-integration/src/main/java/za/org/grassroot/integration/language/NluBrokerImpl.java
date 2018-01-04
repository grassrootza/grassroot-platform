package za.org.grassroot.integration.language;

import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NluBrokerImpl implements NluBroker {

    private String nluHost;
    private Integer nluPort;

    private boolean googleApiKeyPresent;

    private final RestTemplate restTemplate;
    private final Environment environment;

    @Autowired
    public NluBrokerImpl(RestTemplate restTemplate, Environment environment) {
        this.restTemplate = restTemplate;
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        nluHost = environment.getProperty("grassroot.nlu.host", "learning.grassroot.cloud");
        nluPort = environment.getProperty("grassroot.nlu.port", Integer.class, 80);
        googleApiKeyPresent = environment.getProperty("GOOGLE_APPLICATION_CREDENTIALS") != null;
    }

    @Override
    public NluParseResult parseText(String text, String conversationUid) {
        // note : the rest template is autowired to use a default character encoding (UTF 8), so putting encode
        // here will double encode and throw errors, hence leave it out
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                .scheme("http")
                .host(nluHost)
                .port(nluPort)
                .path("/parse")
                .queryParam("text", text);

        if (!StringUtils.isEmpty(conversationUid)) {
            builder = builder.queryParam("uid", conversationUid);
        }

        final URI uriToCall = builder.build().toUri();
        log.info("invoking NLU service, with URI: {}", uriToCall);
        try {
            ResponseEntity<NluServerResponse> response = restTemplate.getForEntity(uriToCall, NluServerResponse.class);
            log.info("returned NLU response: {}", response);
            return response.getBody().getResult();
        } catch (RestClientException e) {
            log.error("Error calling NLU service", e);
            return null;
        }
    }

    @Override
    public List<ConvertedSpeech> speechToText(MultipartFile file, String encoding, int sampleRate) {
        try {
            if (!googleApiKeyPresent) {
                log.error("Error! Attempt to call speech to text without API credentials");
            }

            ByteString rawSpeech = ByteString.copyFrom(file.getBytes());

            log.info("credentials present, converting speech to text via Google Cloud ... ");

            SpeechClient speech = SpeechClient.create();
            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16) // todo : convert from string (or otherwise obtain from client)
                    .setSampleRateHertz(sampleRate)
                    .setLanguageCode("en-ZA")
                    .build();
            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setContent(rawSpeech)
                    .build();

            log.info("built call entities, initiating call to API ...");
            RecognizeResponse response = speech.recognize(config, audio);
            List<SpeechRecognitionResult> results = response.getResultsList();

            log.info("got results, look like : {}", results);
            return results.stream()
                    .map(result -> new ConvertedSpeech(
                            result.getAlternatives(0).getTranscript(),
                            result.getAlternatives(0).getConfidence()
                    )).collect(Collectors.toList());
        } catch (IOException e) {
            log.error("IO exception copying file to audio", e);
            return null;
        }
    }

    @Override
    public NluParseResult speechToIntent(MultipartFile file, String encoding, int sampleRate) {
        List<ConvertedSpeech> speechList = speechToText(file, encoding, sampleRate);
        if (speechList != null) {
            final String fullTranscript = speechList.stream()
                    .map(ConvertedSpeech::getSpeech)
                    .collect(Collectors.joining(" "));
            log.info("full transcript of returned results: {}", fullTranscript);
            return parseText(fullTranscript, null);
        } else {
            log.error("Error! Could not convert file to audio");
            return null;
        }
    }

}
