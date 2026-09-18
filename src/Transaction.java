import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Transaction {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final int id;
    private final List<BillItem> items;
    private final LocalDateTime date;

    public Transaction(int id, List<BillItem> items) {
        this(id, items, LocalDateTime.now());
    }

    // Overloaded constructor: useful when restoring a stored transaction.
    public Transaction(int id, List<BillItem> items, LocalDateTime date) {
        if (id <= 0) throw new IllegalArgumentException("Transaction ID must be positive.");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("A transaction needs at least one item.");
        if (date == null) throw new IllegalArgumentException("Transaction date cannot be null.");
        this.id = id;
        this.items = new ArrayList<>(items);
        this.date = date;
    }

    public int getId() { return id; }
    public List<BillItem> getItems() { return new ArrayList<>(items); }
    public LocalDateTime getDate() { return date; }
    public double getTotal() { return items.stream().mapToDouble(BillItem::getSubtotal).sum(); }

    public String receipt() {
        StringBuilder text = new StringBuilder();
        text.append("Transaction: ").append(id).append("\n");
        text.append("Date: ").append(date.format(FORMATTER)).append("\n");
        text.append("----------------------------------------\n");
        for (BillItem item : items) {
            text.append(String.format("%-22s %3d x Rs.%-7.2f = Rs.%.2f%n",
                    item.getProduct().getName(), item.getQuantity(), item.getProduct().getPrice(), item.getSubtotal()));
        }
        text.append("----------------------------------------\n");
        text.append(String.format("TOTAL: Rs.%.2f%n", getTotal()));
        return text.toString();
    }
}
