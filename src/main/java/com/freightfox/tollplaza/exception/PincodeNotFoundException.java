package com.freightfox.tollplaza.exception;

public class PincodeNotFoundException extends RuntimeException {
    public PincodeNotFoundException(String pincode) {
        super("Pincode not found: " + pincode);
    }
}

