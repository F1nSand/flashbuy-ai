package com.goodskill.auth.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Keeps the existing local demo independent of the production auth schema. */
@Service
@Profile("!production")
public class DemoLoginAttemptLimiter implements LoginAttemptLimiter {

    @Override
    public boolean isBlocked(String account) {
        return false;
    }

    @Override
    public void recordFailure(String account) {
        // The local demo keeps its historical login behavior.
    }

    @Override
    public void clear(String account) {
        // No production throttle state exists in the local demo schema.
    }
}
