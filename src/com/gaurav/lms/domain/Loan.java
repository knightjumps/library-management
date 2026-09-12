package com.gaurav.lms.domain;

import java.time.LocalDate;
import java.util.UUID;

/** Historical lending record for a specific physical copy. */
public final class Loan {
    private final String id = UUID.randomUUID().toString();
    private final String memberId;
    private final String barcode;
    private final LocalDate checkoutDate;
    private LocalDate dueDate;
    private LocalDate returnedDate;
    private int renewalCount;

    public Loan(String memberId, String barcode, LocalDate checkoutDate, int loanDays) {
        this.memberId = memberId;
        this.barcode = barcode;
        this.checkoutDate = checkoutDate;
        this.dueDate = checkoutDate.plusDays(loanDays);
    }

    public String id() { return id; }
    public String memberId() { return memberId; }
    public String barcode() { return barcode; }
    public LocalDate dueDate() { return dueDate; }
    public int renewalCount() { return renewalCount; }
    public boolean isActive() { return returnedDate == null; }

    public void renew(int loanDays) {
        dueDate = dueDate.plusDays(loanDays);
        renewalCount++;
    }

    public void markReturned(LocalDate returnedDate) {
        this.returnedDate = returnedDate;
    }
}
