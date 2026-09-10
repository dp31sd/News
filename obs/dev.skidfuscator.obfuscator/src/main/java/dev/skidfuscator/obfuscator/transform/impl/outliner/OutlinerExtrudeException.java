package dev.skidfuscator.obfuscator.transform.impl.outliner;

public class OutlinerExtrudeException extends RuntimeException {
    public OutlinerExtrudeException() {
    }

    public OutlinerExtrudeException(String message) {
        super(message);
    }

    public OutlinerExtrudeException(String message, Throwable cause) {
        super(message, cause);
    }

    public OutlinerExtrudeException(Throwable cause) {
        super(cause);
    }

    public OutlinerExtrudeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
