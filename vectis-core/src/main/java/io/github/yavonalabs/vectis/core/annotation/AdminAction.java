package io.github.yavonalabs.vectis.core.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdminAction {
    String label() default "";
    String color() default "indigo"; // indigo, emerald, rose, amber, slate
    String icon() default "lightning";
    boolean requiresConfirmation() default true;
    String confirmMessage() default "Are you sure you want to execute this action?";
    String requiredRole() default "";
    RiskLevel risk() default RiskLevel.MODERATE;
    /** Public method on this class returning proposed scalar values without side effects. */
    String previewMethod() default "";
}
