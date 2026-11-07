package com.wiseup.api.presentation.filter;

import java.lang.annotation.*;
import jakarta.ws.rs.NameBinding;

/**
 * JAX-RS name-binding annotation for routes that require a valid session.
 * Apply to a resource class or method to trigger {@link AuthFilter}.
 */
@NameBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAuth {}
