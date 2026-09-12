package com.gaurav.lms.domain;

import com.gaurav.lms.util.Validation;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Title-level catalog metadata shared by all physical copies. */
public final class Book {
    private final String isbn;
    private final String title;
    private final String subject;
    private final LocalDate publicationDate;
    private final List<String> authors;

    public Book(String isbn, String title, String subject, LocalDate publicationDate, List<String> authors) {
        this.isbn = Validation.required(isbn, "isbn");
        this.title = Validation.required(title, "title");
        this.subject = Validation.required(subject, "subject");
        this.publicationDate = Objects.requireNonNull(publicationDate, "publicationDate");
        this.authors = List.copyOf(authors);
    }

    public String isbn() { return isbn; }
    public String title() { return title; }
    public String subject() { return subject; }
    public LocalDate publicationDate() { return publicationDate; }
    public List<String> authors() { return authors; }
}
