package com.gaurav.lms.app;

import com.gaurav.lms.domain.Book;
import com.gaurav.lms.domain.BookItem;
import com.gaurav.lms.domain.Member;
import com.gaurav.lms.domain.Loan;
import com.gaurav.lms.domain.Rack;
import com.gaurav.lms.domain.ReturnResult;
import com.gaurav.lms.exception.LibraryException;
import com.gaurav.lms.policy.DailyFinePolicy;
import com.gaurav.lms.service.Library;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Runs a small end-to-end circulation scenario. */
public final class Main {
    public static void main(String[] args) {
        Library library = new Library(new DailyFinePolicy(new BigDecimal("2.00")), 3, 2, 14);
        library.addObserver(notification -> System.out.println(
                "Notification to " + notification.memberId() + ": " + notification.message()));

        Member alice = library.registerMember("M-100", "Alice");
        Member bob = library.registerMember("M-101", "Bob");

        Book cleanCode = new Book(
                "9780132350884",
                "Clean Code",
                "Software Engineering",
                LocalDate.of(2008, 8, 1),
                List.of("Robert C. Martin"));
        library.addBook(cleanCode);

        BookItem copy = library.addCopy(cleanCode.isbn(), "BC-001", new Rack("A", "12"));
        System.out.println(library.searchByTitle("clean").stream().map(Book::title).toList());

        Loan loan = library.checkout(alice.id(), copy.barcode(), LocalDate.of(2026, 9, 1));
        library.reserve(bob.id(), cleanCode.isbn(), LocalDate.of(2026, 9, 2));

        try {
            library.renew(alice.id(), copy.barcode());
        } catch (LibraryException exception) {
            System.out.println("Renewal rejected: " + exception.getMessage());
        }

        ReturnResult result = library.returnBook(copy.barcode(), LocalDate.of(2026, 9, 20));
        System.out.println("Returned " + loan.id());
        System.out.println("Fine: " + result.fine().amount());
        System.out.println("Status: " + copy.status());

        Loan bobLoan = library.checkout(bob.id(), copy.barcode(), LocalDate.of(2026, 9, 21));
        System.out.println("Reserved copy checked out by " + bob.id() + ": " + bobLoan.id());
    }
}
