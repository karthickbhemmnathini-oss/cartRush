package com.example.cart_rush.service.assistant;

public class AssistantProviderException extends RuntimeException {

    private final String reasonCode;

    public AssistantProviderException(String reasonCode, Throwable cause) {
        super(reasonCode, cause);
        this.reasonCode = reasonCode;
    }

    public AssistantProviderException(String reasonCode) {
        super(reasonCode);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
