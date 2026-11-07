package com.wiseup.api.presentation.filter;

import java.lang.annotation.*;
import jakarta.ws.rs.NameBinding;

/** Name-binding annotation for routes that require termsAccepted = true. */
@NameBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireTerms {}
