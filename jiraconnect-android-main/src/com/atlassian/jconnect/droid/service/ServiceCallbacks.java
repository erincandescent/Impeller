package com.atlassian.jconnect.droid.service;

public final class ServiceCallbacks {

    private ServiceCallbacks() {
        throw new AssertionError("Don't instantiate me");
    }

    public static <S> ServiceCallback<S> nullCallback(Class<S> resultClass) {
        return new ServiceCallback<S>() {
            @Override
            public void onResult(Status status, S result) {
            }
        };
    }

}
