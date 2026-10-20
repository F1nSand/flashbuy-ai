package com.goodskill.auth.service;

public interface LoginAttemptLimiter {

    boolean isBlocked(String account);

    void recordFailure(String account);

    void clear(String account);
}
