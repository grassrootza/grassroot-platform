package za.org.grassroot.webapp.metrics;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.GaugeService;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class MetricsAspect {

    private GaugeService gaugeService;

    @Autowired
    public MetricsAspect(GaugeService gaugeService) {
        this.gaugeService = gaugeService;
    }

    @Around("execution(* za.org.grassroot.services.*.*(..)) || execution(* za.org.grassroot.services.task.*.*(..))")
    public Object doBasicProfiling(ProceedingJoinPoint pjp) throws Throwable {

        long start = System.currentTimeMillis();

        try {
            return pjp.proceed();
        } finally {
            long end = System.currentTimeMillis();
            long time = end - start;
            Class<?> clazz = pjp.getTarget().getClass();
            String methodName = pjp.getSignature().getName();
            this.gaugeService.submit(clazz + "." + methodName, time);
        }
    }
}

