package io.github.yavonalabs.vectis.core.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdminEntity {
    String label() default "";
    String singularLabel() default "";
    String group() default "General";
    boolean readOnly() default false;
}
