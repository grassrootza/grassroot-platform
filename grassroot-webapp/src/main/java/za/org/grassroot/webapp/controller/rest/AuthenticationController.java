package za.org.grassroot.webapp.controller.rest;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.integration.keyprovider.KeyPairProvider;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

    @Autowired
    private KeyPairProvider keyPairProvider;

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> login(@RequestParam("phoneNumber")String phoneNumber,
                                                 @RequestParam("password")String password,
                                                 @RequestParam("clientType") String clientType) {

        //authenticate user before issuing token
        try {

            //TODO: authenticate user before issuing token

            // Generate a token for the user
            String token = generateToken(phoneNumber);

            // Return the token on the response
            return RestUtil.okayResponseWithData(RestMessage.LOGIN_SUCCESS, token);

        } catch (Exception e) {
           logger.error("Failed to generate authentication token for:  " + phoneNumber);
            return RestUtil.errorResponse(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_OTP);
        }

    }

    @RequestMapping(value = "/validateToken", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> validateToken(@RequestParam("token")String token) {
        try {
            Jwts.parser().setSigningKey(getJWTPublicKey()).parse(token);
            return RestUtil.messageOkayResponse(RestMessage.TOKEN_STILL_VALID);
        }
         catch (ExpiredJwtException e) {
             logger.error("Token validation failed. The token is expired.", e);
             return RestUtil.errorResponse(HttpStatus.EXPECTATION_FAILED, RestMessage.TOKEN_EXPIRED);
         }
        catch (Exception e) {
            logger.error("Unexpected token validation error.", e);
            return RestUtil.errorResponse(HttpStatus.EXPECTATION_FAILED, RestMessage.INVALID_TOKEN);
        }

    }

    private String generateToken(String phoneNumber) throws NoSuchAlgorithmException {
        Instant now = Instant.now();
        Instant exp = now.plus(10L, ChronoUnit.MINUTES);
        String jwtToken = Jwts.builder()
                .setSubject(phoneNumber)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(exp))
                .signWith(SignatureAlgorithm.RS256, getJWTPrivateKey())
                .compact();
        return jwtToken;
    }

    private PublicKey getJWTPublicKey() {
        return keyPairProvider.getJWTKey().getPublic();
    }

    private PrivateKey getJWTPrivateKey() {
        return keyPairProvider.getJWTKey().getPrivate();
    }
}
