package com.freightfox.tollplaza.exception;

public class SameSourceDestinationException extends RuntimeException {
    public SameSourceDestinationException(String message) {
        super(message);
    }
}

