package fr.gameurduxvi.randomskin;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.google.gson.Gson;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.gameurduxvi.randomskin.mcapi.ServerData;
import fr.gameurduxvi.randomskin.mojang.Data;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Main {

    private static final boolean ASK_SKIN_ONLY_FOR_NEW_PLAYERS = true;

    public static void main(String[] args) throws SQLException {
        // Register config file
        FileConfig configFile = FileConfig.of("src/main/resources/config.toml");

        // Load config file
        configFile.load();

        // Define gson
        val gson = new Gson();

        // Get servers list
        val servers = readFile("servers.txt");
        val actualServer = new AtomicInteger();

        // Send request to the mcapi to get online players
        val playerDatas = servers.parallelStream()
                .map(ip -> {
                    System.out.println("Pinging " + ip + " (" + actualServer.incrementAndGet() + "/" + servers.size() + ")");
                    return gson.fromJson(getContent("https://mcapi.us/server/status?ip=" + ip), ServerData.class);
                })
                .flatMap(sd -> sd.getPlayers().getSample().stream())
                .filter(pd -> !pd.getId().startsWith("00000000")
                        && !pd.getName().contains("§"))
                .toList();


        // Get old player data
        val allPlayerDatas = readFile("playerdatas.txt");

        // Map new data to string
        val newPlayerDatas = playerDatas.stream()
                .filter(pd -> !allPlayerDatas.contains(pd.getId() + "|" + pd.getName()))
                .map(pd -> pd.getId() + "|" + pd.getName())
                .collect(Collectors.toList());

        // Add new player data to file & list
        newPlayerDatas.forEach(line -> {
            allPlayerDatas.add(line);
            appendToFile("playerdatas.txt", line);
        });

        System.out.println(newPlayerDatas.size() + " new players registered");

        // Send request to mojang to get the skin values
        val skins = new HashMap<String, String>();
        val playerDatasToAsk = ASK_SKIN_ONLY_FOR_NEW_PLAYERS ? newPlayerDatas : allPlayerDatas;
        playerDatasToAsk.forEach(pd -> {
            val uuid = pd.split("\\|")[0];
            System.out.println("Get skin of " + uuid + " (" + (playerDatasToAsk.indexOf(pd) + 1) + "/" + playerDatasToAsk.size() + ")");
            try {
                val content = getContent("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
                skins.put(gson.fromJson(content, Data.class).getProperties().get(0).getValue(), gson.fromJson(content, Data.class).getProperties().get(0).getSignature());
            } catch (Exception ignored) {
            }
        });


        // Add the skin to the postgresql database
        if (configFile.get("postgresql.enabled").equals("true")) {
            val config = new HikariConfig();

            config.setJdbcUrl("jdbc:postgresql://" +
                    configFile.get("postgresql.host") + ":" +
                    configFile.get("postgresql.port") + "/" +
                    configFile.get("postgresql.database"));
            config.setUsername(configFile.get("postgresql.username"));
            config.setPassword(configFile.get("postgresql.password"));

            val source = new HikariDataSource(config);

            val stmt = source.getConnection().prepareStatement("INSERT INTO skins (value, signature) SELECT ?, ? WHERE NOT EXISTS(SELECT * FROM skins where SUBSTRING(value, 43, LENGTH(value))=SUBSTRING(?, 43, LENGTH(?)))");
            skins.forEach((value, signature) -> {
                try {
                    stmt.setString(1, value);
                    stmt.setString(2, signature);
                    stmt.setString(3, value);
                    stmt.setString(4, value);
                    stmt.addBatch();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
            val updated = stmt.executeBatch();

            val amountNewSkins = new AtomicInteger();
            Arrays.stream(updated).forEach(i -> amountNewSkins.set(amountNewSkins.get() + i));

            System.out.println(amountNewSkins.get() + " new skins added");

            source.close();
        }
    }

        /*
        // Add & count the new skins
        val newSkins = new AtomicInteger();
        val oldSkins = Main.readFile("skins.txt");
        skins.stream()
                .filter(value -> oldSkins.stream().noneMatch(oldValue -> oldValue.substring(42, oldValue.length()).equals(value.substring(42, value.length()))))
                .forEach(value -> {
                    newSkins.set(newSkins.get() + 1);
                    appendToFile("skins.txt", value);
                });
        System.out.println(newSkins.get() + " new skins registered");
         */

    @SneakyThrows
    public static String getContent(@NonNull String link) {
        val url = new URL(link);
        val urlConnection = (HttpsURLConnection) url.openConnection();
        val bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
        val content = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null)
            content.append(line);
        bufferedReader.close();
        return content.toString();
    }

    @SneakyThrows
    public static LinkedList<String> readFile(String fileName) {
        val file = new File(fileName);
        if (!file.exists())
            file.createNewFile();
        val reader = new Scanner(file);
        val list = new LinkedList<String>();
        while (reader.hasNextLine()) {
            list.add(reader.nextLine());
        }
        reader.close();
        return list;
    }

    @SneakyThrows
    public static void appendToFile(String file, String string) {
        FileWriter fw = new FileWriter(file, true);
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(string);
        bw.newLine();
        bw.close();
    }
}
