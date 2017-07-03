package za.org.grassroot.webapp.controller.rest;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.crypto.KeyGenerator;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private static KeyGenerator keyGenerator = null;

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
            return RestUtil.errorResponse(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_OTP);
        }

    }

    @RequestMapping(value = "/validateToken", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> validateToken(String token) {
        return null;
    }

    private String generateToken(String phoneNumber) throws NoSuchAlgorithmException {
        Key key = getKeyGenerator().generateKey();
        Instant now = Instant.now();
        Instant exp = now.plus(1L, ChronoUnit.MINUTES);
        String jwtToken = Jwts.builder()
                .setSubject(phoneNumber)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(exp))
                .signWith(SignatureAlgorithm.HS512, key)
                .compact();
        return jwtToken;
    }

    private static KeyGenerator getKeyGenerator() throws NoSuchAlgorithmException {
        if(keyGenerator == null) {
            keyGenerator = KeyGenerator.getInstance("AES");
        }
        return keyGenerator;
    }
}
