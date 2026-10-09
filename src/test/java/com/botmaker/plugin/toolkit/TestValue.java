package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.managed.ManagedMarker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A plugin's managed marker, as a test plugin would ship it. */
@ManagedMarker
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TestValue {

    Id value();

    enum Id { GREETING, PICTURES, WORDS }
}
