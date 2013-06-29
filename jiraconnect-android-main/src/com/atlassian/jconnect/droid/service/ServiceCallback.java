package com.atlassian.jconnect.droid.service;

/**
 * Callback for an asynchronous service operation.
 * 
 * @since 1.0
 */
public interface ServiceCallback<T> {

    public static enum Status {
        SUCCESS,
        FAILURE
    }

    /**
     * Process result. If status is
     * {@link com.atlassian.jconnect.droid.service.ServiceCallback.Status#FAILURE}
     * the <tt>result</tt> is likely to be <code>null</code>.
     * 
     * @param status
     *            status of the call
     * @param result
     *            result of the call
     */
    void onResult(Status status, T result);
}
