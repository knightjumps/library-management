package com.gaurav.lms.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Fine(BigDecimal amount) {
    public Fine {
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }
}
