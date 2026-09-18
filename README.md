# Hardware Shop Billing System

A menu-driven Java console project for a small hardware shop. It handles basic stock billing, walk-in sales, credit/khata sales, customer dues, settlements and recent transaction history.

The project is kept close to the **CSE2006 Programming in Java** topics: classes and constructors, OOP, exception handling, collections, file I/O, multithreading and JDBC.

## What the program does

- Shows product stock and prices.
- Searches products by name.
- Creates walk-in and khata bills.
- Prevents invalid quantities and over-selling.
- Saves sales and stock changes in SQLite.
- Keeps credit customer balances after restarting the program.
- Records settlement payments.
- Saves a text receipt in the `bills/` folder.
- Shows recent sales from the database.
- Uses custom exceptions for common business errors.

## Main Java concepts used

| Topic | Where it is used |
|---|---|
| Class / constructor | `Product`, `BillItem`, `Transaction`, customer classes |
| Encapsulation | Private fields with controlled methods |
| Inheritance | `WalkInCustomer` and `CreditCustomer` extend `Customer` |
| Abstraction | `Customer` is an abstract class |
| Polymorphism | `processTransaction()` is overridden |
| Overloading | `Transaction` constructors; `DatabaseManager` constructors |
| Exception handling | Custom checked exceptions and `try/catch/finally` |
| Collections | `Map`, `List`, `ArrayList` |
| I/O | Receipt files through Java NIO |
| Multithreading | `ReceiptTask implements Runnable` |
| JDBC | SQLite queries using `PreparedStatement` and transactions |

## Project structure

```text
HardwareShopBillingSystem/
|-- src/
|   |-- Product.java
|   |-- BillItem.java
|   |-- Transaction.java
|   |-- Customer.java
|   |-- WalkInCustomer.java
|   |-- CreditCustomer.java
|   |-- ReceiptWriter.java
|   |-- FileReceiptWriter.java
|   |-- ReceiptTask.java
|   |-- DatabaseManager.java
|   |-- HardwareShopApp.java
|   |-- ProductNotFoundException.java
|   |-- InsufficientStockException.java
|   |-- NoOutstandingBalanceException.java
|   |-- ValidationTest.java
|   |-- SystemTest.java
|-- statement.md
|-- schema.sql
|-- run.bat
|-- test.bat
|-- run.sh
|-- test.sh
|-- setup_driver.bat
|-- README.md
|-- HardwareShopBillingSystem_Report.pdf
```

## Requirements

- JDK 17 or later
- SQLite JDBC driver

### Windows - easiest setup

Run:

```bat
setup_driver.bat
```

Then run the project with:

```bat
run.bat
```

Run the checks with:

```bat
test.bat
```

`setup_driver.bat` downloads the pinned SQLite JDBC driver (3.53.4.0) into `lib/sqlite-jdbc.jar`. The application itself does not need an IDE or Maven.

### Manual compile

Windows:

```bat
mkdir bin
javac -cp "lib\sqlite-jdbc.jar" -d bin src\*.java
java -cp "bin;lib\sqlite-jdbc.jar" HardwareShopApp
```

Linux/macOS:

```bash
mkdir -p bin
javac -cp "lib/sqlite-jdbc.jar" -d bin src/*.java
java -cp "bin:lib/sqlite-jdbc.jar" HardwareShopApp
```

## Database

The program creates `hardware_shop.db` in the project folder and uses these tables:

- `products` - stock master
- `customers` - credit customer details and current balance
- `transactions` - sale header
- `transaction_items` - products inside each sale
- `settlements` - payment history

`schema.sql` contains the same table structure for reference.

## Testing

There are two small test programs:

- `ValidationTest.java` checks domain validation, totals, credit handling, settlement and receipt generation.
- `SystemTest.java` uses an in-memory SQLite database to check persistence, rollback and settlement flow.

The project deliberately keeps tests as normal Java classes so they can be run with the same JDK and JDBC setup used by the application.

The project report contains the system architecture, workflow, use-case diagram, class diagram, sequence diagram, ER diagram, testing approach and design decisions.

`statement.md` contains the project problem statement, scope, target users and high-level features.

## Submission notes

Use real screenshots from the running program. Typical evidence should show the menu, inventory, a successful walk-in sale, a credit sale with balance, settlement, transaction history and one invalid-input case.

Do not commit generated files such as `hardware_shop.db`, `bills/`, `bin/` or private credentials. The `.gitignore` file already excludes them.

## Scope

This is a local, single-operator console application. Web UI, cloud hosting, online payments and multi-user authentication are intentionally outside the project scope.
