package com.gaurav.lms.util;

public final class Validation {
    private Validation() {
    }

    public static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value;
    }
}
