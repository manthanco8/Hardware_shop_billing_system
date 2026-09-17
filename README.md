# Hardware Shop Billing System

A command-line billing system for a hardware store. It supports two kinds
of customers — **permanent/khata (credit) customers**, who buy now and
settle their bill later, and **walk-in customers**, who pay immediately —
and sends a WhatsApp notification after every khata purchase and
settlement. All data (inventory, customer accounts, purchase and
settlement history) is stored in a local SQLite database via JDBC, so
nothing is lost when the program is closed and reopened.

This is a plain Java console application — **no GUI, no external
database server required.** It runs entirely from the terminal.

---

## 1. Prerequisites

You need a **JDK (Java Development Kit), version 17 or newer**, installed
and on your system `PATH`. Nothing else needs to be installed separately —
the JDBC driver for SQLite is already bundled in this repo under `lib/`.

Check if you already have one:
```bash
java -version
javac -version
```
If both print a version number ≥ 17, skip to Section 2. Otherwise, install
a JDK for your OS:

- **Windows:** download and run the installer from
  https://adoptium.net/temurin/releases/ (choose "JDK", your OS, and the
  latest LTS version). During install, tick the option to add Java to
  `PATH`.
- **macOS:** `brew install openjdk@21` (using [Homebrew](https://brew.sh)),
  or download from https://adoptium.net/temurin/releases/.
- **Linux (Debian/Ubuntu):** `sudo apt-get update && sudo apt-get install -y openjdk-21-jdk`

Restart your terminal after installing, then re-run the two version
commands above to confirm.

## 2. Get the Code

```bash
git clone https://github.com/<your-username>/<your-repo-name>.git
cd <your-repo-name>
```
(Or download the repository as a ZIP from GitHub and extract it, then
`cd` into the extracted folder.)

## 3. Compile

From the project's root folder (the one containing `src/`, `lib/`, and
this README):

**Linux / macOS:**
```bash
javac -cp "lib/sqlite-jdbc-3.46.1.3.jar" -d bin src/*.java
```

**Windows (Command Prompt or PowerShell):**
```bat
javac -cp "lib/sqlite-jdbc-3.46.1.3.jar" -d bin src/*.java
```
This compiles every `.java` file in `src/` into `.class` files under a
newly created `bin/` folder. A successful run prints nothing (or only a
harmless deprecation note about `URLEncoder.encode`) and exits with no
errors.

## 4. Run

**Linux / macOS:**
```bash
java -cp "bin:lib/sqlite-jdbc-3.46.1.3.jar" HardwareBillingSystem
```

**Windows:** (note the `;` instead of `:` in the classpath)
```bat
java -cp "bin;lib/sqlite-jdbc-3.46.1.3.jar" HardwareBillingSystem
```

You should see:
```
===== HARDWARE SHOP BILLING SYSTEM =====

1. New purchase
2. Settle a khata account
3. View outstanding customers
4. View settlement history
5. Exit
Choose:
```
Type a number and press Enter to use the menu. The program is fully
interactive from the terminal — no additional configuration is required
to try it out; it seeds 5 sample products (paint, brushes, cement, nails,
sandpaper) automatically on first run.

A file named `hardware_shop.db` will appear in the folder you ran the
command from — this is the SQLite database holding all inventory,
customer, and transaction data. Delete it at any time to reset the shop
to a blank slate.

## 5. (Optional) Enable Real WhatsApp Notifications

By default, WhatsApp sending will print a "Failed to send WhatsApp
message" line to the console (because the API key is a placeholder) —
**this is expected and does not affect billing**, since the notification
runs on its own background thread. To make it actually send messages:

1. Save `+34 644 51 90 45` as a contact on the phone that should receive
   messages.
2. From that phone, WhatsApp the contact: `I allow callmebot to send me
   messages`.
3. You'll receive a reply containing a personal API key.
4. Open `src/WhatsAppSender.java`, paste that key into the `API_KEY`
   constant, and re-compile (Section 3).

Reference: https://www.callmebot.com/blog/free-api-whatsapp-messages/

## Project Structure
```
.
├── src/
│   ├── Product.java
│   ├── BillItem.java
│   ├── Transaction.java
│   ├── Customer.java                    (abstract)
│   ├── PermanentCustomer.java           (extends Customer)
│   ├── WalkInCustomer.java              (extends Customer)
│   ├── WhatsAppSender.java              (implements Runnable)
│   ├── DatabaseManager.java             (JDBC persistence layer)
│   ├── ProductNotFoundException.java    (checked exception)
│   ├── InsufficientStockException.java  (checked exception)
│   ├── NoOutstandingDueException.java   (checked exception)
│   └── HardwareBillingSystem.java       (entry point / console menu)
├── bin/                          # created after compiling — .class files
├── lib/
│   └── sqlite-jdbc-3.46.1.3.jar  # JDBC driver (bundled, cross-platform)
└── README.md
```

## How the Data Is Stored (JDBC / SQLite)

`DatabaseManager.java` handles all persistence using plain `java.sql`
(`Connection`, `PreparedStatement`, `ResultSet`) against a local SQLite
file — no external database server needed. Five tables are created
automatically on first run:

| Table | Purpose |
|---|---|
| `products` | inventory: id, name, price, stock |
| `customers` | khata customers: phone, name, current outstanding balance |
| `transactions` | every purchase (khata or walk-in), with a `settled` flag |
| `transaction_items` | line items per transaction, with the unit price *at the time of purchase* |
| `settlements` | a permanent audit trail — one row per completed khata settlement |

Restarting the program does not lose any data: inventory stock, every
khata customer's balance and unsettled purchases, and the full
settlement history are all reloaded from `hardware_shop.db` on startup.

## Troubleshooting

- **`javac: command not found` / `'javac' is not recognized`** — the JDK
  isn't installed or isn't on your `PATH`. Revisit Section 1.
- **`Error: Could not find or load main class HardwareBillingSystem`** —
  make sure you're running the `java` command from the project's root
  folder (the one containing `bin/` and `lib/`), and that Section 3
  completed without errors first.
- **`ClassNotFoundException: org.sqlite.JDBC`** — the SQLite driver jar
  isn't on the classpath. Double-check the `-cp` value matches exactly
  what's shown in Sections 3 and 4, and that `lib/sqlite-jdbc-3.46.1.3.jar`
  exists in your copy of the repo.
- **Windows classpath errors** — Windows uses `;` to separate classpath
  entries, not `:`. Use the Windows-specific commands shown above.
