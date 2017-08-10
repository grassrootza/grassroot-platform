package za.org.grassroot.webapp.model.http;

import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class AuthorizationHeaderTest {

    private String token = "eyJraWQiOiJjYmE4OWMyNy1iMWY5LTQ4ZTYtYjk4My1jZGNjOTVlNzZhNzEiLCJhbGciOiJSUzI1NiJ9.eyJleHAiOjE1MDA0MTE2MjQsImlhdCI6MTUwMDQxMTYyNH0.o6fsZH3TwtPIAzE9isG0-A3OLsMSQxNSy2l0B2SvCbU1XOAzxNMJ2nUa2SXSauOomaBy0kWdjDt5hO3vbZC1WjHwD_W4P1sdXDv_ROgkPL3Ks9qbKN3ZITJku6hnzM8qwrnI-8lc6wotWihAW3Fka5rEktspnppeFggNU8YNick";

    @Test
    public void testWithBearerToken() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer " + token);

        AuthorizationHeader header = new AuthorizationHeader(req);
        assertTrue(header.hasBearerToken());
        assertEquals(token, header.getBearerToken());
    }

    @Test
    public void testWithoutBearerToken() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        when(req.getHeader("Authorisation")).thenReturn(null);

        AuthorizationHeader header = new AuthorizationHeader(req);
        assertFalse(header.hasBearerToken());
    }
}
