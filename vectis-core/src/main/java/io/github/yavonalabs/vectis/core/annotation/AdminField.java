package io.github.yavonalabs.vectis.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface AdminField {
    String description() default "";
    String label() default "";
    int order() default 100;
    /** Presentation only; use AdminIgnore or authorization to restrict access. */
    boolean showInList() default true;
    /** ISO 4217 code. Empty means an ordinary number, never inferred currency. */
    String currency() default "";
}
