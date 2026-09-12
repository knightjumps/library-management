package com.gaurav.lms.domain;

import java.util.Optional;

public record ReturnResult(Loan loan, Fine fine, Optional<Reservation> activatedReservation) {
}
