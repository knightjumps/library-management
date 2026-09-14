package com.gaurav.lms.service;

import com.gaurav.lms.domain.Book;
import com.gaurav.lms.domain.BookItem;
import com.gaurav.lms.domain.BookStatus;
import com.gaurav.lms.domain.Fine;
import com.gaurav.lms.domain.Loan;
import com.gaurav.lms.domain.Member;
import com.gaurav.lms.domain.Notification;
import com.gaurav.lms.domain.Rack;
import com.gaurav.lms.domain.Reservation;
import com.gaurav.lms.domain.ReservationStatus;
import com.gaurav.lms.domain.ReturnResult;
import com.gaurav.lms.exception.LibraryException;
import com.gaurav.lms.notification.NotificationObserver;
import com.gaurav.lms.policy.FinePolicy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Coordinates catalog and circulation workflows. */
public final class Library {
    private final Map<String, Book> booksByIsbn = new HashMap<>();
    private final Map<String, BookItem> copiesByBarcode = new HashMap<>();
    private final Map<String, Member> membersById = new HashMap<>();
    private final List<Loan> loans = new ArrayList<>();
    private final List<Reservation> reservations = new ArrayList<>();
    private final List<NotificationObserver> observers = new ArrayList<>();

    private final FinePolicy finePolicy;
    private final int maxLoans;
    private final int maxRenewals;
    private final int loanDays;

    public Library(FinePolicy finePolicy, int maxLoans, int maxRenewals, int loanDays) {
        this.finePolicy = finePolicy;
        this.maxLoans = maxLoans;
        this.maxRenewals = maxRenewals;
        this.loanDays = loanDays;
    }

    public void addObserver(NotificationObserver observer) {
        observers.add(observer);
    }

    public Member registerMember(String id, String name) {
        if (membersById.containsKey(id)) {
            throw new LibraryException("Member already exists");
        }

        Member member = new Member(id, name);
        membersById.put(id, member);
        return member;
    }

    public void addBook(Book book) {
        if (booksByIsbn.putIfAbsent(book.isbn(), book) != null) {
            throw new LibraryException("ISBN already exists");
        }
    }

    public synchronized BookItem addCopy(String isbn, String barcode, Rack rack) {
        if (copiesByBarcode.containsKey(barcode)) {
            throw new LibraryException("Barcode already exists");
        }

        BookItem copy = new BookItem(barcode, findBook(isbn), rack);
        copiesByBarcode.put(barcode, copy);
        holdCopyForNextWaitingMember(copy);

        return copy;
    }

    public List<Book> searchByTitle(String query) {
        String term = query.toLowerCase();

        return booksByIsbn.values().stream()
                .filter(book -> book.title().toLowerCase().contains(term))
                .toList();
    }

    public List<Book> searchByAuthor(String query) {
        String term = query.toLowerCase();

        return booksByIsbn.values().stream()
                .filter(book -> book.authors().stream()
                        .anyMatch(author -> author.toLowerCase().contains(term)))
                .toList();
    }

    /** In production, use a transaction and row/optimistic lock for this transition. */
    public synchronized Loan checkout(String memberId, String barcode, LocalDate checkoutDate) {
        Member member = findMember(memberId);
        BookItem copy = findCopy(barcode);

        validateCheckout(member, copy);
        completeMemberReservationIfPresent(memberId, copy.barcode());

        Loan loan = new Loan(memberId, barcode, checkoutDate, loanDays);
        loans.add(loan);
        copy.changeStatus(BookStatus.LOANED);

        return loan;
    }

    /**
     * Adds a member to a title's wait-list only when no copy can be borrowed now.
     * A member should check out an available copy directly instead of reserving it.
     */
    public synchronized Reservation reserve(String memberId, String isbn, LocalDate date) {
        findMember(memberId);
        findBook(isbn);

        if (hasActiveReservation(memberId, isbn)) {
            throw new LibraryException("Member already has an active reservation");
        }
        if (hasAvailableCopy(isbn)) {
            throw new LibraryException("A copy is available; check it out instead of reserving it");
        }

        Reservation reservation = new Reservation(memberId, isbn, date);
        reservations.add(reservation);

        return reservation;
    }

    public synchronized Loan renew(String memberId, String barcode) {
        Loan loan = findActiveLoan(barcode);

        if (!loan.memberId().equals(memberId)) {
            throw new LibraryException("Loan belongs to another member");
        }
        if (loan.renewalCount() >= maxRenewals) {
            throw new LibraryException("Renewal limit reached");
        }
        if (nextWaitingReservation(findCopy(barcode).book().isbn()).isPresent()) {
            throw new LibraryException("Cannot renew: another member is waiting");
        }

        loan.renew(loanDays);
        return loan;
    }

    public synchronized ReturnResult returnBook(String barcode, LocalDate returnedOn) {
        BookItem copy = findCopy(barcode);
        Loan loan = findActiveLoan(barcode);
        loan.markReturned(returnedOn);

        Fine fine = finePolicy.calculate(loan, returnedOn);
        Optional<Reservation> nextReservation = holdCopyForNextWaitingMember(copy);

        if (nextReservation.isEmpty()) {
            copy.changeStatus(BookStatus.AVAILABLE);
        }

        return new ReturnResult(loan, fine, nextReservation);
    }

    private void validateCheckout(Member member, BookItem copy) {
        if (!member.isActive()) {
            throw new LibraryException("Inactive member");
        }
        if (activeLoansFor(member.id()).size() >= maxLoans) {
            throw new LibraryException("Borrowing limit reached");
        }
        if (copy.status() == BookStatus.AVAILABLE) {
            return;
        }

        boolean heldForMember = copy.status() == BookStatus.RESERVED
                && pendingPickupReservation(copy.barcode())
                .map(reservation -> reservation.memberId().equals(member.id()))
                .orElse(false);

        if (!heldForMember) {
            throw new LibraryException("Copy is not available for this member");
        }
    }

    private void completeMemberReservationIfPresent(String memberId, String barcode) {
        pendingPickupReservation(barcode)
                .filter(reservation -> reservation.memberId().equals(memberId))
                .ifPresent(reservation -> reservation.changeStatus(ReservationStatus.COMPLETED));
    }

    private boolean hasActiveReservation(String memberId, String isbn) {
        return reservations.stream().anyMatch(reservation ->
                reservation.memberId().equals(memberId)
                        && reservation.isbn().equals(isbn)
                        && (reservation.status() == ReservationStatus.WAITING
                        || reservation.status() == ReservationStatus.PENDING_PICKUP)
        );
    }

    private List<Loan> activeLoansFor(String memberId) {
        return loans.stream()
                .filter(loan -> loan.memberId().equals(memberId) && loan.isActive())
                .toList();
    }

    private boolean hasAvailableCopy(String isbn) {
        return copiesByBarcode.values().stream()
                .anyMatch(copy -> copy.book().isbn().equals(isbn)
                        && copy.status() == BookStatus.AVAILABLE);
    }

    private Optional<Reservation> nextWaitingReservation(String isbn) {
        return reservations.stream()
                .filter(reservation -> reservation.isbn().equals(isbn))
                .filter(reservation -> reservation.status() == ReservationStatus.WAITING)
                .min(Comparator.comparing(Reservation::createdAt));
    }

    private Optional<Reservation> pendingPickupReservation(String barcode) {
        return reservations.stream()
                .filter(reservation -> reservation.status() == ReservationStatus.PENDING_PICKUP)
                .filter(reservation -> barcode.equals(reservation.assignedBarcode()))
                .findFirst();
    }

    /** Assigns a particular copy to the oldest waiter, if the title has one. */
    private Optional<Reservation> holdCopyForNextWaitingMember(BookItem copy) {
        Optional<Reservation> nextReservation = nextWaitingReservation(copy.book().isbn());

        nextReservation.ifPresent(reservation -> {
            reservation.assignCopy(copy.barcode());
            reservation.changeStatus(ReservationStatus.PENDING_PICKUP);
            copy.changeStatus(BookStatus.RESERVED);
            publishAvailability(reservation, copy.book());
        });

        return nextReservation;
    }

    private Loan findActiveLoan(String barcode) {
        return loans.stream()
                .filter(loan -> loan.barcode().equals(barcode) && loan.isActive())
                .findFirst()
                .orElseThrow(() -> new LibraryException("No active loan for this copy"));
    }

    private Member findMember(String memberId) {
        Member member = membersById.get(memberId);

        if (member == null) {
            throw new LibraryException("Unknown member");
        }

        return member;
    }

    private Book findBook(String isbn) {
        Book book = booksByIsbn.get(isbn);

        if (book == null) {
            throw new LibraryException("Unknown ISBN");
        }

        return book;
    }

    private BookItem findCopy(String barcode) {
        BookItem copy = copiesByBarcode.get(barcode);

        if (copy == null) {
            throw new LibraryException("Unknown copy");
        }

        return copy;
    }

    private void publishAvailability(Reservation reservation, Book book) {
        Notification notification = new Notification(
                reservation.memberId(),
                "Reserved title is ready: " + book.title()
        );

        observers.forEach(observer -> observer.notify(notification));
    }
}
