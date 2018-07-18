package za.org.grassroot.webapp.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by paballo on 2016/03/17.
 */
@Slf4j
public class SimpleLoggingInterceptor extends HandlerInterceptorAdapter {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        log.debug("Handling request, IP appears as: {}", request.getRemoteAddr());
        List<String> headers = Collections.list(request.getHeaderNames());
        Map<String, String> allHeaders = headers.stream().collect(Collectors.toMap(name -> name, request::getHeader));
        log.info("All headers: {}", allHeaders);
        long startTime = System.currentTimeMillis();
        request.setAttribute("startTime", startTime);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler, ModelAndView modelAndView) {

        response.addHeader("X-Grassroot-Logged", "true");

        long startTime = (Long) request.getAttribute("startTime");
        long endTime = System.currentTimeMillis();
        long executeTime = endTime - startTime;

        log.debug("{} ms : [{}]", executeTime, handler);
    }
}