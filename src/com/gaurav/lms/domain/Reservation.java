package com.gaurav.lms.domain;

import java.time.LocalDate;
import java.util.UUID;

/** FIFO title-level wait-list entry. */
public final class Reservation {
    private final String id = UUID.randomUUID().toString();
    private final String memberId;
    private final String isbn;
    private final LocalDate createdAt;
    private ReservationStatus status = ReservationStatus.WAITING;
    private String assignedBarcode;

    public Reservation(String memberId, String isbn, LocalDate createdAt) {
        this.memberId = memberId;
        this.isbn = isbn;
        this.createdAt = createdAt;
    }

    public String id() { return id; }
    public String memberId() { return memberId; }
    public String isbn() { return isbn; }
    public LocalDate createdAt() { return createdAt; }
    public ReservationStatus status() { return status; }
    public String assignedBarcode() { return assignedBarcode; }

    public void changeStatus(ReservationStatus status) {
        this.status = status;
    }

    /** Assigns the physical copy that has been placed on hold for this request. */
    public void assignCopy(String barcode) {
        this.assignedBarcode = barcode;
    }
}
