-- SQLite schema used by DatabaseManager.java
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS products(
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    price REAL NOT NULL CHECK(price >= 0),
    stock INTEGER NOT NULL CHECK(stock >= 0)
);

CREATE TABLE IF NOT EXISTS customers(
    phone TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    balance REAL NOT NULL DEFAULT 0 CHECK(balance >= 0)
);

CREATE TABLE IF NOT EXISTS transactions(
    id INTEGER PRIMARY KEY,
    customer_phone TEXT,
    customer_type TEXT NOT NULL,
    created_at TEXT NOT NULL,
    total REAL NOT NULL,
    FOREIGN KEY(customer_phone) REFERENCES customers(phone)
);

CREATE TABLE IF NOT EXISTS transaction_items(
    transaction_id INTEGER NOT NULL,
    product_id TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK(quantity > 0),
    unit_price REAL NOT NULL,
    PRIMARY KEY(transaction_id, product_id),
    FOREIGN KEY(transaction_id) REFERENCES transactions(id) ON DELETE CASCADE,
    FOREIGN KEY(product_id) REFERENCES products(id)
);

CREATE TABLE IF NOT EXISTS settlements(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_phone TEXT NOT NULL,
    amount REAL NOT NULL CHECK(amount > 0),
    settled_at TEXT NOT NULL,
    FOREIGN KEY(customer_phone) REFERENCES customers(phone)
);
