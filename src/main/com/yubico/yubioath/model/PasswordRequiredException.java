package com.yubico.yubioath.model;

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/23/13
 * Time: 4:26 PM
 * To change this template use File | Settings | File Templates.
 */
public class PasswordRequiredException extends Exception {
    private final byte[] id;
    private final boolean missing;

    public PasswordRequiredException(String message, byte[] id, boolean missing) {
        super(message);
        this.id = id;
        this.missing = missing;
    }

    public byte[] getId() {
        return id;
    }

    public boolean isMissing() {
        return missing;
    }
}
