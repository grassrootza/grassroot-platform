package za.org.grassroot.webapp.model.http;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;

@Slf4j
public class AuthorizationHeader {

    private static String BEARER_TOKEN_WORD = "bearer";

    private HttpServletRequest request;

    public AuthorizationHeader(HttpServletRequest request) {
        this.request =request;

    }

    public String getBearerToken() {
        return this.getHeader().substring(BEARER_TOKEN_WORD.length() + 1);
    }

    public boolean hasBearerToken() {
        if (this.isNull()) {
            return false;
        }
        return getHeader().toLowerCase().startsWith(BEARER_TOKEN_WORD);
    }

    public boolean doesNotHaveBearerToken() {
        return !this.hasBearerToken();
    }

    private boolean isNull() {
        return this.getHeader() == null || this.getHeader().length() == 0;
    }


    private String getHeader() {
        return this.request.getHeader("Authorization");
    }
}
