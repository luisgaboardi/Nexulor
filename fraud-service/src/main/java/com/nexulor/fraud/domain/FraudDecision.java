package com.nexulor.fraud.domain;

public enum FraudDecision {
    APPROVE,
    REJECT,
    REVIEW;

    public static FraudDecision fromWire(com.nexulor.grpc.fraud.v1.Decision wire) {
        if (wire == com.nexulor.grpc.fraud.v1.Decision.APPROVE) {
            return APPROVE;
        }
        if (wire == com.nexulor.grpc.fraud.v1.Decision.REJECT) {
            return REJECT;
        }
        return REVIEW;
    }

    public com.nexulor.grpc.fraud.v1.Decision toWire() {
        return switch (this) {
            case APPROVE -> com.nexulor.grpc.fraud.v1.Decision.APPROVE;
            case REJECT -> com.nexulor.grpc.fraud.v1.Decision.REJECT;
            case REVIEW -> com.nexulor.grpc.fraud.v1.Decision.REVIEW;
        };
    }
}
