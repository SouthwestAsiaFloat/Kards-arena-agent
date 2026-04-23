package com.southwestasiafloat.backend.application.service;

public class ServiceBusyException extends RuntimeException {

    public ServiceBusyException(String message) {
        super(message);
    }
}
