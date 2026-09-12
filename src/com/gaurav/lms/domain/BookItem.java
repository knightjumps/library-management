package com.gaurav.lms.domain;

import com.gaurav.lms.util.Validation;
import java.util.Objects;

/** One physical, independently borrowable copy of a title. */
public final class BookItem {
    private final String barcode;
    private final Book book;
    private final Rack rack;
    private BookStatus status = BookStatus.AVAILABLE;

    public BookItem(String barcode, Book book, Rack rack) {
        this.barcode = Validation.required(barcode, "barcode");
        this.book = Objects.requireNonNull(book, "book");
        this.rack = Objects.requireNonNull(rack, "rack");
    }

    public String barcode() { return barcode; }
    public Book book() { return book; }
    public Rack rack() { return rack; }
    public BookStatus status() { return status; }

    public void changeStatus(BookStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }
}
