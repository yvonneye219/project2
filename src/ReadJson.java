import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class ReadJson {

    public static void main(String[] args) {
        ReadJson app = new ReadJson();
        SwingUtilities.invokeLater(() -> app.new Viewer().setVisible(true));
    }
    //Start the app and show the interface

    public JSONObject getCountryObject(String countryName) throws Exception {
        String encodedName = URLEncoder.encode(countryName.trim(), "UTF-8"); //Removes extra spaces, and converts the name into a URL-safe format.
        URL url = new URL("https://restcountries.com/v3.1/name/" + encodedName);
//Ask the internet for this country’s data and return it as a usable object.

        HttpURLConnection conn = (HttpURLConnection) url.openConnection(); //Opens a connection to the URL and prepares to send a request.
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");//Basically telling the server "Please send the data back as JSON"

        if (conn.getResponseCode() != 200) {
            throw new RuntimeException("Country not found");
        }//The program checks the HTTP status code. 200 means success. Anything else means an error.

        StringBuilder jsonText = new StringBuilder();//This creates a container to hold the incoming JSON text.
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        //Set up a tool to read the server’s response as text
        String line;
        while ((line = br.readLine()) != null) jsonText.append(line);
        //Reads each line until the end of the response.
        // Adds each line into jsonText so I end up with one full JSON string.
        br.close();
        conn.disconnect();
        //Closes the stream and releases network resources

        JSONArray arr = (JSONArray) new JSONParser().parse(jsonText.toString());
        return (JSONObject) arr.get(0);
        //REST Countries returns an array of matches (even if I only wanted one country).
        // parse the text into a JSONArray.
        // return the first element as a JSONObject.
    }

    public String getFlagPngUrl(JSONObject countryObj) {
        JSONObject flags = (JSONObject) countryObj.get("flags");
        return (String) flags.get("png");
    }
    //In the country JSON, "flags" is an object that contains URLs.
    //I return the "png" URL string.

    public String getCurrency(JSONObject countryObj) {
        JSONObject currencies = (JSONObject) countryObj.get("currencies");
        if (currencies == null || currencies.isEmpty()) return "Currency: N/A";
        //"currencies" might not exist for some entries. If missing, the code returns a safe fallback.

        String code = (String) currencies.keySet().iterator().next();
        //currencies is keyed by currency code (like "USD", "EUR"). And I grab the first key.
        JSONObject cur = (JSONObject) currencies.get(code);
// Gets the object for that currency code (contains name/symbol).
        String name = (cur != null && cur.get("name") != null) ? cur.get("name").toString() : code;
        String symbol = (cur != null && cur.get("symbol") != null) ? cur.get("symbol").toString() : "";
        //If fields are missing, avoid null pointer errors.
        //Use the code if name missing, and use empty string if symbol missing.

        if (!symbol.isEmpty()) return "Currency: " + name + " (" + symbol + ")";
        return "Currency: " + name;
    }
// //If symbol exists, show it. Otherwise, just show the name.

    public String getCapital(JSONObject countryObj) {
        JSONArray cap = (JSONArray) countryObj.get("capital");
        if (cap == null || cap.isEmpty()) return "Capital: N/A";
        return "Capital: " + cap.get(0).toString();
    }
    //"capital" is usually an array (some countries have multiple).
    // code shows the first one.

    public String getRegion(JSONObject countryObj) {
        Object r = countryObj.get("region");
        return "Region: " + (r == null ? "N/A" : r.toString());
    }
    //"region" is a simple field. The code returns "N/A" if missing.


    public ImageIcon downloadFlag(String pngUrl) throws Exception {
        //Downloads the PNG and returns it as an ImageIcon
        URL url = new URL(pngUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        //Regular GET request to download the flag PNG.

        InputStream in = conn.getInputStream();
        BufferedImage img = ImageIO.read(in);
        //Reads the downloaded bytes and converts them into an image in memory.

        return new ImageIcon(img);
        //Wraps the image as ImageIcon so it can be shown in a JLabel.
    }

    public byte[] downloadBytes(String fileUrl) throws Exception {
        //Downloads a file and returns the raw bytes.
        //I need this because OpenAI image edits expects the image as binary data.
        URL url = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        InputStream in = conn.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        //ByteArrayOutputStream stores bytes in memory and grows as needed.

        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
//Reads the stream in chunks (8KB at a time).
//Writes each chunk into baos.
        in.close();
        conn.disconnect();
        return baos.toByteArray();
        //Converts your collected bytes to a byte array.
    }



    private String getOpenAIApiKey() {
        String key = System.getenv("OPENAI_API_KEY");
        //Reads your API key from the environment, instead of hard-coding it in GitHub.
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY is not set.");
        }
        //If missing, crash early with a clear message (prevents confusing errors later).
        return key.trim();
        //Removes accidental spaces.
    }

    public ImageIcon cartoonizeFlag(byte[] flagPngBytes, String prompt) throws Exception {
        //Sends the flag image + prompt to OpenAI. Returns the edited image as an ImageIcon.
        if (flagPngBytes == null) throw new IllegalStateException("Load a country flag first!");
//Prevents calling the API before users have loaded any flag.
        String boundary = "----JavaBoundary" + UUID.randomUUID().toString().replace("-", "");
        //multipart/form-data requires a boundary string to separate parts.
        //I make it unique so it won’t appear inside the file data by accident.
        URL url = new URL("https://api.openai.com/v1/images/edits");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//Prepares the API endpoint connection.
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + getOpenAIApiKey());
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        OutputStream out = conn.getOutputStream();
        //Opens a stream to write the request body.

        writeFormField(out, boundary, "model", "gpt-image-1.5");   // or gpt-image-1
        writeFormField(out, boundary, "prompt", prompt);
        writeFileField(out, boundary, "image[]", "flag.png", "image/png", flagPngBytes); // IMPORTANT: image[]
        writeFormField(out, boundary, "size", "1024x1024");
//sending several “parts” inside one HTTP request:
//some parts are text fields (like model or prompt)
//one part is the image file (binary data)
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        //Closes the multipart body.
        out.flush();
        out.close();
        //Sends everything and closes output.

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
//If successful, read normal response stream.
//If error, read error stream (otherwise I lose the message explaining what went wrong).
        StringBuilder responseText = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        String line;
        while ((line = br.readLine()) != null) responseText.append(line);
        br.close();
        conn.disconnect();
//Reads the JSON response into a string.
        if (code < 200 || code >= 300) {
            throw new RuntimeException("OpenAI API error (" + code + "): " + responseText);
        }
        //If OpenAI says error, throw with full details.

        JSONObject json = (JSONObject) new JSONParser().parse(responseText.toString());
        JSONArray data = (JSONArray) json.get("data");
        JSONObject first = (JSONObject) data.get(0);
        //Response JSON contains "data" array; you use the first generated image.

        String b64 = (String) first.get("b64_json");
        byte[] imageBytes = Base64.getDecoder().decode(b64);
//OpenAI returns the image as base64 text.
//The code decodes base64 back into real image bytes.
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
        return new ImageIcon(img);
        //Converts the bytes into an image, then to Swing ImageIcon.
    }

    private void writeFormField(OutputStream out, String boundary, String name, String value) throws IOException {
        //Writes one “text field” part.
        String part =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" +
                        value + "\r\n";
        //Multipart format rules:
        //Start with --boundary
        //Headers like Content-Disposition
        //Blank line
        //The value
        //End with newline
        out.write(part.getBytes(StandardCharsets.UTF_8));
        //Sends it as UTF-8 bytes.
    }

    private void writeFileField(OutputStream out, String boundary, String name, String filename, String contentType, byte[] fileBytes) throws IOException {
       //Writes one “file upload” part.

        String header =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n" +
                        "Content-Type: " + contentType + "\r\n\r\n";
        //Same multipart rules, but includes filename and content type.

        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        //Writes header, then raw binary file bytes, then a newline.
    }


    public class Viewer extends JFrame {

        JTextField input;
        JButton search;
        JButton cartoonize;

        JLabel flagLabel;
        JLabel aiLabel;
        JLabel infoLabel;
        //Input field, buttons, labels for original flag/AI image, and a label for country info.

        JSONObject lastCountry = null;
        byte[] lastFlagBytes = null;

        public Viewer() {
            setTitle("Country Flag → AI Cartoon Character");
            setSize(900, 520);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(10, 10));
            //Sets window title, size, close behavior.
            //Centers the window on screen.
            //Uses BorderLayout with gaps.

            infoLabel = new JLabel(" ", SwingConstants.CENTER);
            add(infoLabel, BorderLayout.NORTH);
            //Top label for currency/capital/region.

            JPanel center = new JPanel(new GridLayout(1, 2, 10, 10));
            flagLabel = new JLabel("Original Flag", SwingConstants.CENTER);
            aiLabel = new JLabel("AI Cartoon Character", SwingConstants.CENTER);
            center.add(flagLabel);
            center.add(aiLabel);
            add(center, BorderLayout.CENTER);
            //Middle area with two columns: left = original flag, right = AI result.

            JPanel bottom = new JPanel(new GridLayout(1, 3, 8, 8));
            input = new JTextField();
            search = new JButton("Load Flag + Info");
            cartoonize = new JButton("Cartoonize Flag (AI)");
            bottom.add(input);
            bottom.add(search);
            bottom.add(cartoonize);
            add(bottom, BorderLayout.SOUTH);
            //Bottom area: text field + two buttons.

            search.addActionListener(e -> loadCountry());
            cartoonize.addActionListener(e -> runCartoonize());
            //When buttons are clicked, run my methods.
        }

        private void loadCountry() {
            try {//Wraps everything so errors don’t crash the whole GUI
                lastCountry = getCountryObject(input.getText());
                //Uses my API method to get country JSON based on the text field.
                String pngUrl = getFlagPngUrl(lastCountry);
                lastFlagBytes = downloadBytes(pngUrl);
                //Gets the flag PNG URL and downloads the raw bytes for AI use later.

                ImageIcon flag = downloadFlag(pngUrl);
                flagLabel.setIcon(flag);
                flagLabel.setText("");
                //Downloads a displayable flag and shows it.
                //Clears text so the image is visible.

                String info = getCurrency(lastCountry) + " | " + getCapital(lastCountry) + " | " + getRegion(lastCountry);
                infoLabel.setText(info);
//Builds a single info line and displays it.
            } catch (Exception ex) {
                flagLabel.setText("Error: " + ex.getMessage());
            }
            //If anything fails, show the error message on the left label.
        }

        private void runCartoonize() {
            aiLabel.setText("Generating...");
            aiLabel.setIcon(null);
            //Immediately updates UI so user sees feedback.

            new Thread(() -> {
                //Creates a background thread.
                //This prevents the GUI from freezing while waiting for the network.
                try {
                    String prompt =
                            "Turn this country's flag into a cartoon character mascot using its colors and patterns.";
//The text you send to OpenAI.
                    ImageIcon img = cartoonizeFlag(lastFlagBytes, prompt);
                 //   Calls your API method and gets the AI image.
                    SwingUtilities.invokeLater(() -> {
                        aiLabel.setIcon(scaleToLabel(img, aiLabel));
                        aiLabel.setText("");
                    });
                    //Important Swing rule again: GUI updates should happen on the EDT.
                    //I also scale the image to fit the label.

                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() ->
                            aiLabel.setText("AI Error: " + ex.getMessage())
                    );
                }//Displays a readable error message on the GUI if API fails.
            }).start();//Actually starts the thread
        }
        }

    private ImageIcon scaleToLabel(ImageIcon icon, JLabel label) {
        if (icon == null) return null;

        int w = label.getWidth();
        int h = label.getHeight();
        //Reads the current label size
        if (w <= 0 || h <= 0) {
            w = 400;
            h = 400;
        }
        //If the label hasn’t been laid out yet, width/height might be 0.
        //the code uses a reasonable default.

        Image scaled = icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
        //Creates a resized version of the image to match the label.
        //SCALE_SMOOTH makes it look less pixelated.
    }
    }

