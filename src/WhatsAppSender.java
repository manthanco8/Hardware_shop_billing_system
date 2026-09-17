import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Sends a WhatsApp message using the free CallMeBot API.
 *
 * SETUP (one-time, real phone required):
 * 1. Save +34 644 51 90 45 as a contact on WhatsApp.
 * 2. Send it the message: "I allow callmebot to send me messages"
 * 3. You'll get a reply with your personal API key. Paste it below.
 * Docs: https://www.callmebot.com/blog/free-api-whatsapp-messages/
 *
 * Implements Runnable so it can run on its own thread and not block
 * the billing counter while the network call happens.
 */
public class WhatsAppSender implements Runnable {

    private static final String API_KEY = "YOUR_CALLMEBOT_API_KEY";

    private String phoneNumber;
    private String message;

    public WhatsAppSender(String phoneNumber, String message) {
        this.phoneNumber = phoneNumber;
        this.message = message;
    }

    @Override
    public void run() {
        try {
            String encodedMessage = URLEncoder.encode(message, "UTF-8");
            String urlString = "https://api.callmebot.com/whatsapp.php?phone="
                    + phoneNumber + "&text=" + encodedMessage + "&apikey=" + API_KEY;

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            System.out.println("WhatsApp API response: " + response);
        } catch (Exception e) {
            System.out.println("Failed to send WhatsApp message: " + e.getMessage());
        }
    }
}
