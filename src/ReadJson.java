import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.swing.*;
import java.awt.*;

public class ReadJson {

    public static void main(String args[]) throws Exception {
        JSONObject file = new JSONObject();
        file.put("Full Name", "Ritu Sharma");
        file.put("Roll No.", 1704310046);
        file.put("Tution Fees", 65400);

        System.out.print(file.get("Tution Fees"));

        ReadJson readingIsWhat = new ReadJson();

        SwingUtilities.invokeLater(() -> {
            PokemonViewer viewer = readingIsWhat.new PokemonViewer();
            viewer.setVisible(true);
        });
    }

    public ReadJson() {}

    public JSONObject pull(String pokemonName) throws Exception {
        String output;
        String totalJson = "";

        URL url = new URL("https://pokeapi.co/api/v2/pokemon/" + pokemonName.toLowerCase());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");

        if (conn.getResponseCode() != 200)
            throw new RuntimeException("HTTP " + conn.getResponseCode());

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));

        while ((output = br.readLine()) != null) {
            totalJson += output;
        }

        conn.disconnect();

        JSONParser parser = new JSONParser();
        return (JSONObject) parser.parse(totalJson);
    }

    public class PokemonViewer extends JFrame {

        private JTextField nameField;
        private JTextArea abilitiesArea;
        private JTextField searchField;
        private JButton searchButton;

        public PokemonViewer() {
            setTitle("Pokemon Viewer");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(450, 350);
            setLocationRelativeTo(null);

            setLayout(new BorderLayout(10, 10));

            JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));

            nameField = new JTextField();
            nameField.setEditable(false);
            nameField.setBorder(BorderFactory.createTitledBorder("Name"));

            abilitiesArea = new JTextArea(3, 20);
            abilitiesArea.setEditable(false);
            abilitiesArea.setLineWrap(true);
            abilitiesArea.setWrapStyleWord(true);
            JScrollPane abilitiesScroll = new JScrollPane(abilitiesArea);
            abilitiesScroll.setBorder(BorderFactory.createTitledBorder("Abilities"));

            topPanel.add(nameField);
            topPanel.add(abilitiesScroll);

            add(topPanel, BorderLayout.NORTH);

            add(new JPanel(), BorderLayout.CENTER);

            JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));

            searchField = new JTextField();
            searchField.setBorder(BorderFactory.createTitledBorder("Pokemon Name"));
            bottomPanel.add(searchField, BorderLayout.CENTER);

            searchButton = new JButton("Search");
            bottomPanel.add(searchButton, BorderLayout.EAST);

            add(bottomPanel, BorderLayout.SOUTH);

            searchButton.addActionListener(event -> {
                try {
                    String pokemonName = searchField.getText().trim();
                    JSONObject jsonObject = ReadJson.this.pull(pokemonName);

                    String name = (String) jsonObject.get("name");
                    nameField.setText(name);

                    JSONArray abilities = (JSONArray) jsonObject.get("abilities");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < abilities.size(); i++) {
                        JSONObject abilityObject = (JSONObject) abilities.get(i);
                        JSONObject abilityInfo = (JSONObject) abilityObject.get("ability");
                        sb.append("-").append(abilityInfo.get("name")).append("\n");
                    }

                    abilitiesArea.setText(sb.toString());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
    }
}
