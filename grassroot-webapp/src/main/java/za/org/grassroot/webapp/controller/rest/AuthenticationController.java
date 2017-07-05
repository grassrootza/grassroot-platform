package za.org.grassroot.webapp.controller.rest;

import io.jsonwebtoken.ExpiredJwtException;
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

    private static  Key key = null;

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
    public ResponseEntity<ResponseWrapper> validateToken(@RequestParam("token")String token) {
        try {
            Jwts.parser().setSigningKey(getKey()).parse(token);
            return RestUtil.messageOkayResponse(RestMessage.TOKEN_STILL_VALID);
        }
         catch (ExpiredJwtException e) {
             return RestUtil.errorResponse(HttpStatus.EXPECTATION_FAILED, RestMessage.TOKEN_EXPIRED);
         }
        catch (Exception e) {
            return RestUtil.errorResponse(HttpStatus.EXPECTATION_FAILED, RestMessage.INVALID_TOKEN);
        }

    }

    private String generateToken(String phoneNumber) throws NoSuchAlgorithmException {
        Key key = getKey();
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

    private static Key getKey() throws NoSuchAlgorithmException {
        if(key == null) {
            key = KeyGenerator.getInstance("AES").generateKey();
        }
        return key;
    }
}
