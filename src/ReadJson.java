import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class ReadJson {

    public static void main(String[] args) {
        ReadJson app = new ReadJson();
        SwingUtilities.invokeLater(() -> app.new Viewer().setVisible(true));
    }


    public JSONObject getCountryObject(String countryName) throws Exception {
        String encodedName = URLEncoder.encode(countryName.trim(), "UTF-8");

        URL url = new URL("https://restcountries.com/v3.1/name/" + encodedName);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");

        if (conn.getResponseCode() != 200) {
            throw new RuntimeException("Country not found");
        }

        StringBuilder jsonText = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = br.readLine()) != null) {
            jsonText.append(line);
        }
        conn.disconnect();

        JSONArray arr = (JSONArray) new JSONParser().parse(jsonText.toString());
        return (JSONObject) arr.get(0);
    }

    public String getFlagPngUrl(JSONObject countryObj) {
        JSONObject flags = (JSONObject) countryObj.get("flags");
        return (String) flags.get("png");
    }

    public ImageIcon downloadFlag(String pngUrl) throws Exception {
        URL url = new URL(pngUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        InputStream in = conn.getInputStream();
        BufferedImage img = ImageIO.read(in);
        conn.disconnect();

        return new ImageIcon(img);
    }

    public class Viewer extends JFrame {

        JTextField input;
        JButton search;
        JLabel imageLabel;

        public Viewer() {
            setTitle("Rest Countries Flag Viewer");
            setSize(500, 350);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(10, 10));

            imageLabel = new JLabel(
                    "Type a country name (Korea, Brazil, etc.)",
                    SwingConstants.CENTER
            );
            add(imageLabel, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new BorderLayout(5, 5));
            input = new JTextField();
            input.setBorder(BorderFactory.createTitledBorder("Country Name"));
            bottom.add(input, BorderLayout.CENTER);

            search = new JButton("Search");
            bottom.add(search, BorderLayout.EAST);
            add(bottom, BorderLayout.SOUTH);

            search.addActionListener(e -> {
                try {
                    JSONObject country = getCountryObject(input.getText());
                    String pngUrl = getFlagPngUrl(country);
                    ImageIcon flag = downloadFlag(pngUrl);

                    imageLabel.setIcon(flag);
                    imageLabel.setText("");

                } catch (Exception ex) {
                    imageLabel.setIcon(null);
                    imageLabel.setText("Error: " + ex.getMessage());
                }
            });
        }
    }
}

//my plan: add the currency (already found the website)

//AI for cartoon: https://app.kira.art/generator/36fa51fb-91c4-47cc-92d8-3262a78da6de


