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
            System.out.println("[VECTIS DRY RUN] Blocked external call: " + joinPoint.getSignature().getName());
            
            Class<?> returnType = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getReturnType();
            if (returnType == void.class) return null;
            if (returnType == boolean.class) return true;
            if (returnType == String.class) return "MOCK_DRY_RUN_RESPONSE";
            
            return null;
        }
        return joinPoint.proceed();
    }
}
