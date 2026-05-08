package com.southwestasiafloat.backend.application.analysis.support;

/**
 * 服务繁忙异常，用于表示系统容量不足或请求受限。
 */

public class ServiceBusyException extends RuntimeException {

    public ServiceBusyException(String message) {
        super(message);
    }
}
