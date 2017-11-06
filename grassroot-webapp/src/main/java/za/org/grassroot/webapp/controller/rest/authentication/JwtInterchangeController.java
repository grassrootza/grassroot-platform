package za.org.grassroot.webapp.controller.rest.authentication;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.PublicCredentials;

/**
 * Created by luke on 2017/05/22.
 */
@RestController
@RequestMapping("/api/jwt")
public class JwtInterchangeController {

    private final JwtService jwtService;

    @Autowired
    public JwtInterchangeController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @RequestMapping("/public/credentials")
    public @ResponseBody PublicCredentials getPublicCredentials() {
        return jwtService.getPublicCredentials();
    }

}   