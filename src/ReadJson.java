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
        br.close();
        conn.disconnect();

        JSONArray arr = (JSONArray) new JSONParser().parse(jsonText.toString());
        return (JSONObject) arr.get(0);
    }

    public String getFlagPngUrl(JSONObject countryObj) {
        JSONObject flags = (JSONObject) countryObj.get("flags");
        return (String) flags.get("png");
    }

    public String getCurrency(JSONObject countryObj) {
        JSONObject currencies = (JSONObject) countryObj.get("currencies");
        if (currencies == null || currencies.isEmpty()) return "Currency: N/A";

        String code = (String) currencies.keySet().iterator().next();
        JSONObject cur = (JSONObject) currencies.get(code);

        String name = (cur != null && cur.get("name") != null) ? cur.get("name").toString() : code;
        String symbol = (cur != null && cur.get("symbol") != null) ? cur.get("symbol").toString() : "";

        if (!symbol.isEmpty()) return "Currency: " + name + " (" + symbol + ")";
        return "Currency: " + name;
    }

    public String getCapital(JSONObject countryObj) {
        JSONArray cap = (JSONArray) countryObj.get("capital");
        if (cap == null || cap.isEmpty()) return "Capital: N/A";
        return "Capital: " + cap.get(0).toString();
    }

    public String getRegion(JSONObject countryObj) {
        Object r = countryObj.get("region");
        return "Region: " + (r == null ? "N/A" : r.toString());
    }

    public ImageIcon downloadFlag(String pngUrl) throws Exception {
        URL url = new URL(pngUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        InputStream in = conn.getInputStream();
        BufferedImage img = ImageIO.read(in);
        in.close();
        conn.disconnect();

        return new ImageIcon(img);
    }

    public byte[] downloadBytes(String fileUrl) throws Exception {
        URL url = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        InputStream in = conn.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);

        in.close();
        conn.disconnect();
        return baos.toByteArray();
    }



    private String getOpenAIApiKey() {
        String key = System.getenv("OPENAI_API_KEY");
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY is not set.");
        }
        return key.trim();
    }

    public ImageIcon cartoonizeFlag(byte[] flagPngBytes, String prompt) throws Exception {
        if (flagPngBytes == null) throw new IllegalStateException("Load a country flag first!");

        String boundary = "----JavaBoundary" + UUID.randomUUID().toString().replace("-", "");
        URL url = new URL("https://api.openai.com/v1/images/edits");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + getOpenAIApiKey());
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        OutputStream out = conn.getOutputStream();

        writeFormField(out, boundary, "model", "gpt-image-1.5");   // or gpt-image-1
        writeFormField(out, boundary, "prompt", prompt);
        writeFileField(out, boundary, "image[]", "flag.png", "image/png", flagPngBytes); // IMPORTANT: image[]
        writeFormField(out, boundary, "size", "1024x1024");

        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        out.close();

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();

        StringBuilder responseText = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        String line;
        while ((line = br.readLine()) != null) responseText.append(line);
        br.close();
        conn.disconnect();

        if (code < 200 || code >= 300) {
            throw new RuntimeException("OpenAI API error (" + code + "): " + responseText);
        }

        JSONObject json = (JSONObject) new JSONParser().parse(responseText.toString());
        JSONArray data = (JSONArray) json.get("data");
        JSONObject first = (JSONObject) data.get(0);

        String b64 = (String) first.get("b64_json");
        byte[] imageBytes = Base64.getDecoder().decode(b64);

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
        return new ImageIcon(img);
    }

    private void writeFormField(OutputStream out, String boundary, String name, String value) throws IOException {
        String part =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" +
                        value + "\r\n";
        out.write(part.getBytes(StandardCharsets.UTF_8));
    }

    private void writeFileField(OutputStream out, String boundary, String name, String filename, String contentType, byte[] fileBytes) throws IOException {
        String header =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n" +
                        "Content-Type: " + contentType + "\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }


    public class Viewer extends JFrame {

        JTextField input;
        JButton search;
        JButton cartoonize;

        JLabel flagLabel;
        JLabel aiLabel;
        JLabel infoLabel;

        JSONObject lastCountry = null;
        byte[] lastFlagBytes = null;

        public Viewer() {
            setTitle("Country Flag → AI Cartoon Character");
            setSize(900, 520);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(10, 10));

            infoLabel = new JLabel(" ", SwingConstants.CENTER);
            add(infoLabel, BorderLayout.NORTH);

            JPanel center = new JPanel(new GridLayout(1, 2, 10, 10));
            flagLabel = new JLabel("Original Flag", SwingConstants.CENTER);
            aiLabel = new JLabel("AI Cartoon Character", SwingConstants.CENTER);
            center.add(flagLabel);
            center.add(aiLabel);
            add(center, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new GridLayout(1, 3, 8, 8));
            input = new JTextField();
            search = new JButton("Load Flag + Info");
            cartoonize = new JButton("Cartoonize Flag (AI)");
            bottom.add(input);
            bottom.add(search);
            bottom.add(cartoonize);
            add(bottom, BorderLayout.SOUTH);

            search.addActionListener(e -> loadCountry());
            cartoonize.addActionListener(e -> runCartoonize());
        }

        private void loadCountry() {
            try {
                lastCountry = getCountryObject(input.getText());
                String pngUrl = getFlagPngUrl(lastCountry);
                lastFlagBytes = downloadBytes(pngUrl);

                ImageIcon flag = downloadFlag(pngUrl);
                flagLabel.setIcon(flag);
                flagLabel.setText("");

                String info = getCurrency(lastCountry) + " | " + getCapital(lastCountry) + " | " + getRegion(lastCountry);
                infoLabel.setText(info);

            } catch (Exception ex) {
                flagLabel.setText("Error: " + ex.getMessage());
            }
        }

        private void runCartoonize() {
            aiLabel.setText("Generating...");
            aiLabel.setIcon(null);

            new Thread(() -> {
                try {
                    String prompt =
                            "Turn this country's flag into a cartoon character mascot using its colors and patterns.";

                    ImageIcon img = cartoonizeFlag(lastFlagBytes, prompt);

                    SwingUtilities.invokeLater(() -> {
                        aiLabel.setIcon(scaleToLabel(img, aiLabel));
                        aiLabel.setText("");
                    });

                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() ->
                            aiLabel.setText("AI Error: " + ex.getMessage())
                    );
                }
            }).start();
        }
        }

    private ImageIcon scaleToLabel(ImageIcon icon, JLabel label) {
        if (icon == null) return null;

        int w = label.getWidth();
        int h = label.getHeight();

        if (w <= 0 || h <= 0) {
            w = 400;
            h = 400;
        }

        Image scaled = icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }
    }

