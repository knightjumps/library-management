# Database Design for the Library Management System

## 1. Database choice and design goals

A relational database such as PostgreSQL is a good default because checkout, return, reservation, renewal, and fine-payment operations require:

- ACID transactions.
- Foreign-key relationships.
- Uniqueness and check constraints.
- Consistent concurrent state changes.
- Durable transaction and audit history.

The most important modeling decision is to separate a book title or edition from each physical copy:

```text
Book / Edition
      |
      | 1-to-many
      v
  Book Copy
      |
      | 1-to-many over time
      v
     Loan

Member ---- Reservation ---- Book
Member ---- Fine ----------- Loan
```

For example, the library can have one catalog entry for *Clean Code* and five physical copies with five different barcodes.

## 2. Books and editions

### `books`

The `books` table stores metadata shared by every copy of the same edition.

| Column | Suggested type | Purpose |
|---|---|---|
| `id` | UUID or BIGINT PK | Stable internal identifier |
| `isbn` | VARCHAR UNIQUE, nullable if required | External edition identifier |
| `title` | VARCHAR NOT NULL | Display title |
| `subject` | VARCHAR | Category or classification |
| `publisher` | VARCHAR | Publisher name |
| `language` | VARCHAR | Language of this edition |
| `publication_date` | DATE | Publication date |
| `created_at` | TIMESTAMP | Creation audit field |
| `updated_at` | TIMESTAMP | Last-update audit field |
| `deleted_at` | TIMESTAMP nullable | Soft deletion |

ISBN should normally be unique, but it should not necessarily be the primary key:

- Some older publications do not have an ISBN.
- ISBN identifies an edition, not the abstract literary work.
- Internal IDs keep foreign keys stable if an external identifier must be corrected.

For a more advanced system, split the model into `works` and `editions`. A work represents the abstract title, while editions represent hardcover, paperback, translated, or revised publications. For most LLD interviews, treating `books` as editions is sufficient.

### `authors`

| Column | Suggested type | Purpose |
|---|---|---|
| `id` | UUID or BIGINT PK | Author identifier |
| `name` | VARCHAR NOT NULL | Author name |
| `biography` | TEXT nullable | Optional description |

### `book_authors`

Books and authors have a many-to-many relationship.

| Column | Suggested type | Purpose |
|---|---|---|
| `book_id` | FK to `books.id` | Book/edition |
| `author_id` | FK to `authors.id` | Author |
| `author_order` | INT | Preserves display order |

The primary key can be `(book_id, author_id)`.

## 3. Physical inventory

### `racks`

| Column | Suggested type | Purpose |
|---|---|---|
| `id` | UUID or BIGINT PK | Rack identifier |
| `section` | VARCHAR | Library section |
| `rack_number` | VARCHAR | Rack or shelf number |
| `floor` | VARCHAR nullable | Floor identifier |
| `location_description` | VARCHAR nullable | Human-readable directions |

### `book_copies`

A row represents one physical item that can be borrowed independently.

| Column | Suggested type | Purpose |
|---|---|---|
| `id` | UUID or BIGINT PK | Internal copy ID |
| `book_id` | FK to `books.id` | Shared title/edition |
| `barcode` | VARCHAR UNIQUE NOT NULL | Scannable copy identifier |
| `rack_id` | FK to `racks.id` | Current physical location |
| `status` | VARCHAR or database ENUM | Operational state |
| `acquired_at` | DATE | Acquisition date |
| `price` | DECIMAL(10,2) | Replacement value |
| `version` | BIGINT | Optimistic-locking version |
| `created_at` | TIMESTAMP | Creation time |
| `updated_at` | TIMESTAMP | Last-update time |

Possible copy statuses are:

```text
AVAILABLE
RESERVED
LOANED
LOST
DAMAGED
WITHDRAWN
```

The barcode must have a unique constraint. Avoid treating an `available_count` column on `books` as the source of truth because it can drift away from individual copy states. Availability should be derived from `book_copies`, or maintained as a carefully controlled cached counter when scale demands it.

## 4. Users, roles, and library cards

### `users`

| Column | Suggested type | Purpose |
|---|---|---|
| `id` | UUID or BIGINT PK | User identifier |
| `name` | VARCHAR NOT NULL | Display name |
| `email` | VARCHAR UNIQUE | Email address |
| `phone` | VARCHAR nullable | Phone number |
| `status` | VARCHAR | ACTIVE, SUSPENDED, or CLOSED |
| `created_at` | TIMESTAMP | Creation time |
| `updated_at` | TIMESTAMP | Last-update time |

A small system can keep a single `role` column such as `MEMBER` or `LIBRARIAN`. A more extensible RBAC model uses separate role tables.

### `roles`

| Column | Suggested type | Purpose |
|---|---|---|
| `id` | UUID or BIGINT PK | Role identifier |
| `name` | VARCHAR UNIQUE | Role name |

### `user_roles`

| Column | Suggested type | Purpose |
|---|---|---|
| `user_id` | FK to `users.id` | User |
| `role_id` | FK to `roles.id` | Assigned role |

The composite primary key can be `(user_id, role_id)`.

### `library_cards`

| Column | Suggested type | Purpose |
|---|---|---|
| `id` | UUID or BIGINT PK | Card identifier |
| `member_id` | FK to `users.id` | Card owner |
| `card_number` | VARCHAR UNIQUE | Public card number |
| `issued_at` | DATE | Issue date |
| `expires_at` | DATE | Expiration date |
| `status` | VARCHAR | ACTIVE, EXPIRED, LOST, etc. |

If every member always has exactly one card and card history is irrelevant, these columns can be placed directly in `users`.

## 5. Loans

### `loans`

A loan is a historical transaction, not just the current status of a copy.

| Column | Suggested type | Purpose |
|---|---|---|
| `id` | UUID or BIGINT PK | Loan identifier |
| `book_copy_id` | FK to `book_copies.id` | Exact borrowed copy |
| `member_id` | FK to `users.id` | Borrowing member |
| `checked_out_by` | FK to `users.id` | Librarian/operator |
| `checkout_at` | TIMESTAMP | Checkout time |
| `due_at` | TIMESTAMP | Due time |
| `returned_at` | TIMESTAMP nullable | Null while active |
| `returned_to` | FK to `users.id`, nullable | Return operator |
| `renewal_count` | INT DEFAULT 0 | Number of renewals |
| `status` | VARCHAR | ACTIVE, RETURNED, or LOST |
| `created_at` | TIMESTAMP | Audit timestamp |

The two pieces of state answer different questions:

- `book_copies.status`: Can this physical copy be borrowed now?
- `loans`: Who borrowed it, when is it due, and what happened historically?

PostgreSQL can prevent two simultaneous active loans for one copy:

```sql
CREATE UNIQUE INDEX one_active_loan_per_copy
ON loans(book_copy_id)
WHERE returned_at IS NULL;
```

This protects the invariant even if application logic contains a bug.

## 6. Reservations and copy allocation

### `reservations`

A reservation starts as a title-level request. When a copy becomes available, that exact copy is assigned to the reservation.

| Column | Suggested type | Purpose |
|---|---|---|
| `id` | UUID or BIGINT PK | Reservation identifier |
| `book_id` | FK to `books.id` | Requested title |
| `member_id` | FK to `users.id` | Requesting member |
| `assigned_copy_id` | FK to `book_copies.id`, nullable | Copy allocated for pickup |
| `status` | VARCHAR | Reservation lifecycle |
| `requested_at` | TIMESTAMP | Queue ordering |
| `available_at` | TIMESTAMP nullable | Time a copy was assigned |
| `pickup_expires_at` | TIMESTAMP nullable | Hold expiration |
| `completed_at` | TIMESTAMP nullable | Checkout completion |
| `cancelled_at` | TIMESTAMP nullable | Cancellation time |

Possible statuses are:

```text
WAITING
PENDING_PICKUP
COMPLETED
CANCELLED
EXPIRED
```

The oldest waiter can be selected with:

```sql
SELECT *
FROM reservations
WHERE book_id = :bookId
  AND status = 'WAITING'
ORDER BY requested_at, id
LIMIT 1;
```

Ordering by both timestamp and ID gives deterministic behavior when timestamps are equal.

Prevent duplicate active reservations:

```sql
CREATE UNIQUE INDEX one_active_reservation_per_member_and_book
ON reservations(member_id, book_id)
WHERE status IN ('WAITING', 'PENDING_PICKUP');
```

A reservation is not required for a normal checkout:

```text
AVAILABLE copy
    -> member checks it out directly

No AVAILABLE copy
    -> member joins the reservation queue

Copy returned or added
    -> assign it to the first WAITING reservation
    -> copy becomes RESERVED
    -> reservation becomes PENDING_PICKUP

Assigned member checks it out
    -> reservation becomes COMPLETED
    -> copy becomes LOANED
```

Other available copies of the same title remain borrowable. A reserved copy is borrowable only by the member to whom that particular copy was assigned.

## 7. Renewals

A simple design stores `renewal_count` and the current `due_at` in `loans`. Renewing increments the count and changes the due date.

When audit history matters, add `loan_renewals`.

### `loan_renewals`

| Column | Suggested type | Purpose |
|---|---|---|
| `id` | UUID or BIGINT PK | Renewal identifier |
| `loan_id` | FK to `loans.id` | Renewed loan |
| `previous_due_at` | TIMESTAMP | Old due date |
| `new_due_at` | TIMESTAMP | New due date |
| `renewed_at` | TIMESTAMP | Renewal time |
| `renewed_by` | FK to `users.id` | Member or librarian |

Renewal should be rejected when another member is waiting for the title, the member is blocked, or the maximum renewal count has been reached.

## 8. Fines and payments

Do not store only a member's current total fine because that loses the cause, payment, and waiver history.

### `fines`

| Column | Suggested type | Purpose |
|---|---|---|
| `id` | UUID or BIGINT PK | Fine identifier |
| `loan_id` | FK to `loans.id` | Source loan |
| `member_id` | FK to `users.id` | Responsible member |
| `type` | VARCHAR | OVERDUE, LOST_BOOK, DAMAGED_BOOK |
| `amount` | DECIMAL(10,2) | Assessed amount |
| `status` | VARCHAR | UNPAID, PARTIALLY_PAID, PAID, WAIVED |
| `assessed_at` | TIMESTAMP | Assessment time |
| `waived_at` | TIMESTAMP nullable | Waiver time |
| `waived_by` | FK to `users.id`, nullable | Librarian who waived it |

### `fine_payments`

| Column | Suggested type | Purpose |
|---|---|---|
| `id` | UUID or BIGINT PK | Payment identifier |
| `fine_id` | FK to `fines.id` | Fine being paid |
| `amount` | DECIMAL(10,2) | Payment amount |
| `payment_method` | VARCHAR | Cash, card, UPI, etc. |
| `external_transaction_id` | VARCHAR UNIQUE nullable | Payment-provider identifier |
| `paid_at` | TIMESTAMP | Payment time |
| `status` | VARCHAR | PENDING, SUCCESS, FAILED, REFUNDED |

Fine and payment are separate because a fine may be paid in installments, waived without payment, or retried after a failed payment. Use Java `BigDecimal` and database `DECIMAL`; never use floating-point types for money.

## 9. Notifications and reliable events

### `notifications`

| Column | Suggested type | Purpose |
|---|---|---|
| `id` | UUID or BIGINT PK | Notification identifier |
| `member_id` | FK to `users.id` | Recipient |
| `type` | VARCHAR | BOOK_AVAILABLE, OVERDUE, etc. |
| `channel` | VARCHAR | EMAIL, SMS, PUSH |
| `payload` | JSON or TEXT | Renderable message data |
| `status` | VARCHAR | PENDING, SENT, FAILED |
| `created_at` | TIMESTAMP | Creation time |
| `sent_at` | TIMESTAMP nullable | Successful delivery time |
| `failure_reason` | TEXT nullable | Last failure |
| `retry_count` | INT | Delivery attempts |

For reliable asynchronous delivery, use the transactional outbox pattern.

### `outbox_events`

| Column | Suggested type | Purpose |
|---|---|---|
| `id` | UUID PK | Event identifier |
| `event_type` | VARCHAR | Event name |
| `aggregate_id` | UUID/BIGINT | Related domain object |
| `payload` | JSON | Event data |
| `created_at` | TIMESTAMP | Creation time |
| `published_at` | TIMESTAMP nullable | Publication time |

During a return, the transaction updates the loan, copy, reservation, and outbox row together. A background publisher later sends the event to Kafka or the notification service. This prevents a committed reservation from losing its notification event.

## 10. Important indexes

Recommended indexes include:

```text
books(isbn) UNIQUE
book_copies(barcode) UNIQUE
book_copies(book_id, status)
loans(member_id, status)
loans(book_copy_id, status)
loans(due_at, status)
reservations(book_id, status, requested_at)
reservations(member_id, status)
fines(member_id, status)
notifications(status, created_at)
outbox_events(published_at, created_at)
```

Their purposes are:

- `book_copies(book_id, status)`: quickly locate an available copy.
- `reservations(book_id, status, requested_at)`: efficiently locate the next waiter.
- `loans(member_id, status)`: enforce borrowing limits and show active loans.
- `loans(due_at, status)`: find overdue loans for scheduled processing.
- `fines(member_id, status)`: calculate unpaid dues.
- `notifications(status, created_at)`: process pending or failed notifications.
- `outbox_events(published_at, created_at)`: publish unprocessed events.

For fuzzy title and author search, a normal B-tree index is insufficient. Small systems can use PostgreSQL full-text or trigram indexes. Large systems can use Elasticsearch or OpenSearch as a derived search index while PostgreSQL remains the source of truth.

## 11. Checkout transaction and concurrency

Checkout must prevent two members from borrowing the same physical copy.

One solution is pessimistic locking:

```sql
BEGIN;

SELECT *
FROM book_copies
WHERE id = :copyId
FOR UPDATE;
```

While holding the lock, validate:

- The member is active.
- The borrowing limit has not been reached.
- Fine policy permits checkout.
- The copy is `AVAILABLE`; or it is `RESERVED` specifically for this member.

Then insert the loan, change the copy status, and complete an assigned reservation:

```sql
INSERT INTO loans (...);

UPDATE book_copies
SET status = 'LOANED',
    version = version + 1
WHERE id = :copyId;

UPDATE reservations
SET status = 'COMPLETED',
    completed_at = NOW()
WHERE assigned_copy_id = :copyId
  AND member_id = :memberId
  AND status = 'PENDING_PICKUP';

COMMIT;
```

An alternative is an atomic conditional update:

```sql
UPDATE book_copies
SET status = 'LOANED',
    version = version + 1
WHERE id = :copyId
  AND status = 'AVAILABLE';
```

If zero rows are updated, another transaction changed the copy first.

| Concurrency approach | Advantages | Disadvantages |
|---|---|---|
| Pessimistic row lock | Straightforward correctness | Lock contention and possible deadlocks |
| Optimistic versioning | Efficient when conflicts are uncommon | Requires conflict detection and retry |
| Conditional update | Small and atomic | Complex business rules may need more queries |

## 12. Return transaction

Return should execute atomically:

```text
1. Lock the active loan and physical copy.
2. Set loan.returned_at and loan.status.
3. Calculate and insert a fine when overdue.
4. Find the oldest WAITING reservation.
5. If a waiter exists:
      assign this copy to that reservation
      reservation -> PENDING_PICKUP
      copy -> RESERVED
   Otherwise:
      copy -> AVAILABLE
6. Insert a notification event into the outbox.
7. Commit.
```

For concurrent returns, PostgreSQL can select different waiters safely:

```sql
SELECT *
FROM reservations
WHERE book_id = :bookId
  AND status = 'WAITING'
ORDER BY requested_at, id
FOR UPDATE SKIP LOCKED
LIMIT 1;
```

`SKIP LOCKED` lets another return transaction move past a reservation already claimed by a concurrent transaction.

## 13. Database constraints

Important invariants should be enforced in the database as well as application code:

- Unique ISBN when present.
- Unique copy barcode.
- Foreign keys on every relationship.
- `fine.amount >= 0`.
- `renewal_count >= 0`.
- `due_at > checkout_at`.
- `returned_at IS NULL OR returned_at >= checkout_at`.
- At most one active loan per copy.
- At most one active reservation per member and title.
- `assigned_copy_id` must be present for `PENDING_PICKUP`.

Example:

```sql
CHECK (
    status <> 'PENDING_PICKUP'
    OR assigned_copy_id IS NOT NULL
)
```

Database constraints protect the data if a future service, administrative script, or faulty code path bypasses the normal Java validation.

## 14. Deletion and audit strategy

Do not physically delete books, copies, or members referenced by historical loans or financial records. Prefer status changes or soft deletion:

```text
book_copies.status = WITHDRAWN
users.status = CLOSED
books.deleted_at = timestamp
```

Loan, fine, and payment records should be append-only or updated only through tightly controlled state transitions. This preserves reporting, dispute resolution, and auditability.

## 15. Interview-ready answer

A concise answer in an interview can be:

> I would use PostgreSQL because circulation operations require strong transactional consistency. I would separate `books`, which hold title and edition metadata, from `book_copies`, which hold the barcode, rack, and availability of each physical item. Members are stored in `users`; `loans` preserve checkout history; and `reservations` maintain a FIFO title-level queue. When a copy becomes available, its ID is assigned to the oldest waiting reservation.
>
> Checkout and return run inside database transactions. I would enforce a unique active loan per copy, use row locking or an atomic conditional update during checkout, and index copy availability, active member loans, reservation ordering, and overdue dates. Fines and payments remain separate for auditability. Notifications use an outbox event so the database change and eventual message delivery remain reliable.

The goal is not to memorize every column. Explain why every table exists, which relationships it represents, and which business invariant the database must protect.

## 16. Complete table inventory

| Table | Required or optional | Primary use case |
|---|---|---|
| `books` | Required | Stores title/edition metadata shared by every physical copy |
| `works` | Optional advanced model | Represents the abstract literary work across multiple editions |
| `authors` | Required for normalized authors | Stores author details |
| `book_authors` | Required when books can have multiple authors | Implements the many-to-many relationship between books and authors |
| `racks` | Required for physical libraries | Stores reusable shelf and location information |
| `book_copies` | Required | Tracks every physical copy, barcode, location, value, and current status |
| `users` | Required | Stores members, librarians, and their account status/contact details |
| `roles` | Optional for extensible RBAC | Defines roles such as MEMBER, LIBRARIAN, and ADMIN |
| `user_roles` | Optional for extensible RBAC | Assigns one or more roles to each user |
| `library_cards` | Optional | Tracks card number, issue date, expiration, replacement, and status |
| `loans` | Required | Stores current and historical checkout/return transactions |
| `reservations` | Required | Maintains FIFO waitlists and assigns returned copies to members |
| `loan_renewals` | Optional but useful for auditing | Records every due-date extension and who performed it |
| `fines` | Required when fines are supported | Records overdue, lost-book, and damaged-book charges |
| `fine_payments` | Required when payments are supported | Records fine-payment attempts, installments, and external transaction IDs |
| `notifications` | Optional but recommended | Tracks email, SMS, and push delivery status and retries |
| `outbox_events` | Recommended for reliable asynchronous integration | Reliably publishes committed domain events to messaging/notification systems |

