import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileReceiptWriter implements ReceiptWriter {
    private final Path outputDirectory;

    public FileReceiptWriter(String directory) {
        outputDirectory = Path.of(directory);
    }

    @Override
    public String writeReceipt(Transaction transaction, String customerName) throws IOException {
        Files.createDirectories(outputDirectory);
        String safeName = customerName.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path file = outputDirectory.resolve("bill_" + safeName + "_" + transaction.getId() + ".txt");
        String content = "HARDWARE SHOP\nCustomer: " + customerName + "\n" + transaction.receipt();
        Files.writeString(file, content);
        return file.toString();
    }
}
