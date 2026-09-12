package com.gaurav.lms.policy;

import com.gaurav.lms.domain.Fine;
import com.gaurav.lms.domain.Loan;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class DailyFinePolicy implements FinePolicy {
    private final BigDecimal dailyRate;

    public DailyFinePolicy(BigDecimal dailyRate) {
        this.dailyRate = dailyRate;
    }

    @Override
    public Fine calculate(Loan loan, LocalDate returnedOn) {
        long lateDays = Math.max(0, returnedOn.toEpochDay() - loan.dueDate().toEpochDay());
        return new Fine(dailyRate.multiply(BigDecimal.valueOf(lateDays)));
    }
}
