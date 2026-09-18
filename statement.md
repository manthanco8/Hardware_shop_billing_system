# Project Statement

## Problem Statement
Small hardware shops often record sales, stock movement and credit purchases manually. This can lead to calculation mistakes, incorrect stock counts and difficulty tracking customer dues. The proposed Hardware Shop Billing System provides a simple Java-based solution for recording sales, updating inventory and maintaining khata balances.

## Scope
The project covers local product inventory, walk-in sales, credit sales, receipt generation, customer outstanding balances, settlement and recent transaction history. Data is persisted in a local SQLite database through JDBC.

## Target Users
- Hardware shop owner
- Shop cashier/operator
- Small shop staff handling billing and credit records

## High-Level Features
1. View and validate inventory.
2. Create walk-in and credit sales.
3. Update stock atomically with transaction records.
4. Maintain persistent khata balances.
5. Generate receipt files using Java I/O.
6. Settle outstanding credit accounts.
7. View recent transaction history.
8. Search products by name.
9. Handle invalid input and domain-specific errors safely.
