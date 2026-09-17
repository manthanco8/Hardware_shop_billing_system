import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Transaction {

    private List<BillItem> items;
    private Date date;
    private int id = -1; // -1 = not yet persisted to the database

    public Transaction(List<BillItem> items) {
        this.items = items;
        this.date = new Date();
    }

    // Used when reconstructing a transaction that already happened
    // (e.g. reloading pending khata purchases from the database on startup)
    public Transaction(List<BillItem> items, Date date) {
        this.items = items;
        this.date = date;
    }

    public Transaction(List<BillItem> items, Date date, int id) {
        this.items = items;
        this.date = date;
        this.id = id;
    }

    public List<BillItem> getItems() { return items; }
    public Date getDate() { return date; }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public double getTotal() {
        double total = 0;
        for (BillItem item : items) {
            total += item.getSubtotal();
        }
        return total;
    }

    public String toReceiptLine() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm");
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(sdf.format(date)).append("]\n");
        for (BillItem item : items) {
            sb.append(String.format(" %-18s x%-3d Rs.%-8.2f%n",
                    item.getProduct().getName(), item.getQuantity(), item.getSubtotal()));
        }
        sb.append(String.format(" Subtotal: Rs.%.2f%n", getTotal()));
        return sb.toString();
    }
}
