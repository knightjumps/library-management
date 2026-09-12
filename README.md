# Library Management System (LLD)

A runnable, in-memory Java implementation of a library management system.

## Run

```bash
cd src
javac -d ../out $(find . -name '*.java')
java -cp ../out com.gaurav.lms.app.Main
```

## Design

```text
Book (title metadata) 1 ----- * BookItem (individual borrowable copy)
                                      |
                                      1
                                     Rack

Member 1 ----- * Loan * ----- 1 BookItem
Member 1 ----- * Reservation * ----- 1 Book
```

`Book` deliberately holds shared catalog information such as ISBN, title, author, and subject. `BookItem` owns copy-specific state: barcode, rack, and availability. This prevents a title with multiple copies from being represented as duplicate books.

## Supported workflows

- Register members and add catalog titles/copies
- Search by title or author
- Check out an available copy while enforcing a borrowing limit
- Reserve a title with FIFO ordering
- Renew a loan unless the renewal limit is reached or a member is waiting
- Return a copy, calculate an overdue fine, and reserve it for the next member
- Publish an availability notification through an observer interface

## Extension points

- `FinePolicy` is a Strategy: replace `DailyFinePolicy` for different member or item policies.
- `NotificationObserver` is an Observer: replace the console lambda with email, SMS, Kafka, or Spring application-event adapters.
- `synchronized` models the critical checkout/return transition in memory. In a production Spring Boot application, put these operations in transactions and use optimistic locking or a conditional SQL update on `BookItem.status`.

## Deliberate scope boundaries

The example uses in-memory collections to keep the LLD visible. A production implementation would add repositories, persisted loan/fine records, authentication/RBAC, payment integration, hold-expiry jobs, and a search index for large catalogs.
