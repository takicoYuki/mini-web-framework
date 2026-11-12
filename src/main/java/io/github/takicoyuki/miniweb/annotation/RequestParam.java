package io.github.takicoyuki.miniweb.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** クエリパラメータ（?name=value）を引数で受け取る。 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestParam {
    String value();

    /** 指定がないときの値 */
    String defaultValue() default "";

    /** false にすると、指定がないとき null を渡す（defaultValue があればそちらを優先） */
    boolean required() default true;
}
