package io.github.yavonalabs.vectis.core.context;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class DryRunProtectionAspect {

    @Around("@annotation(io.github.yavonalabs.vectis.core.annotation.ExternalApiCall) || execution(* com.stripe..*.*(..))")
    public Object protectExternalCalls(ProceedingJoinPoint joinPoint) throws Throwable {
        if (DryRunContextHolder.isDryRun()) {
            throw new IllegalStateException("External calls are not allowed in an action preview.");
        }
        return joinPoint.proceed();
    }
}
