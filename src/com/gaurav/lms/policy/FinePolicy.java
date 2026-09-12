package com.gaurav.lms.policy;

import com.gaurav.lms.domain.Fine;
import com.gaurav.lms.domain.Loan;
import java.time.LocalDate;

/** Strategy extension point for configurable fine rules. */
public interface FinePolicy {
    Fine calculate(Loan loan, LocalDate returnedOn);
}
