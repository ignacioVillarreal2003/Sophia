package com.sophia.documentworkspace.application.security.policy;

/**
 * A single rule of the resource authorization chain. Policies are stateless
 * beans composed by the {@code ResourceAccessEvaluator}: mandatory policies
 * must all allow, discretionary policies allow when at least one grants.
 */
public interface ResourcePolicy {

    AccessDecision evaluate(PolicyContext context);
}
