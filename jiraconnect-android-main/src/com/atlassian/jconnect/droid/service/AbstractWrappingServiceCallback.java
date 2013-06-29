package com.atlassian.jconnect.droid.service;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.ref.WeakReference;

import android.util.Log;

public abstract class AbstractWrappingServiceCallback<O, R> implements ServiceCallback<R> {

    private static final String LOG_TAG = "AbstractWrappingServiceCallback";

    protected final WeakReference<O> ownerReference;

    public AbstractWrappingServiceCallback(O owner) {
        this.ownerReference = new WeakReference<O>(checkNotNull(owner, "owner"));
    }

    @Override
    public final void onResult(Status status, R result) {
        final O owner = ownerReference.get();
        if (owner != null) {
            if (status == ServiceCallback.Status.SUCCESS) {
                onSuccess(owner, result);
            } else {
                onFailure(owner, result);
            }
        } else {
            Log.w(LOG_TAG, "Owner of '" + this + "' is gone, cannot pass result!");
        }
    }

    protected abstract void onSuccess(O owner, R result);

    protected abstract void onFailure(O owner, R result);
}
