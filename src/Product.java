public class Product {
    private final String id;
    private final String name;
    private final double price;
    private int stock;

    public Product(String id, String name, double price, int stock) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Product ID cannot be empty.");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Product name cannot be empty.");
        if (price < 0 || stock < 0) throw new IllegalArgumentException("Price and stock cannot be negative.");
        this.id = id.trim().toUpperCase();
        this.name = name.trim();
        this.price = price;
        this.stock = stock;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getStock() { return stock; }

    public void reduceStock(int quantity) throws InsufficientStockException {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive.");
        if (quantity > stock) throw new InsufficientStockException("Not enough stock for " + name + ". Available: " + stock);
        stock -= quantity;
    }

    public void setStockFromDatabase(int stock) {
        if (stock < 0) throw new IllegalArgumentException("Stock cannot be negative.");
        this.stock = stock;
    }

    @Override
    public String toString() {
        return String.format("%-5s %-24s Rs.%-8.2f Stock: %d", id, name, price, stock);
    }
}
