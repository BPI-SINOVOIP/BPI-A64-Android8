package com.google.android.tradefed.util;

/**
 * An exception class for a Cloud Task Queue related error.
 */
public class CloudTaskQueueException extends Exception {

    private static final long serialVersionUID = 1L;

    public CloudTaskQueueException(String msg) {
        super(msg);
    }

    public CloudTaskQueueException(String msg, Throwable cause) {
        super(msg, cause);
    }

}

