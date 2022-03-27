package fr.gameurduxvi.randomskin;

import com.google.gson.Gson;
import fr.gameurduxvi.randomskin.mcapi.ServerData;
import fr.gameurduxvi.randomskin.mojang.Data;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) {
        // Define gson
        val gson = new Gson();
        
        // Get servers list
        val servers = readFile("servers.txt");
        
        // Send request to the mcapi to get online players
        val playerDatas = servers.stream()
                .map(ip -> {
                    System.out.println("Pinging " + ip + " (" + (servers.indexOf(ip) + 1) + "/" + servers.size() + ")");
                    return gson.fromJson(getContent("https://mcapi.us/server/status?ip=" + ip), ServerData.class);
                })
                .flatMap(sd -> sd.getPlayers().getSample().stream())
                .filter(pd -> !pd.getId().startsWith("00000000")
                        && !pd.getName().contains("§"))
                .collect(Collectors.toList());


        // Add & count the new player data's
        val newPlayerDatas = new AtomicInteger();
        val oldPlayerDatas = readFile("playerdatas.txt");
        playerDatas.stream()
                .filter(pd -> !oldPlayerDatas.contains(pd.getId() + "|" + pd.getName()))
                .forEach(pd -> {
                    newPlayerDatas.set(newPlayerDatas.get() + 1);
                    appendToFile("playerdatas.txt", pd.getId() + "|" + pd.getName());
                });
        System.out.println(newPlayerDatas.get() + " new players registered");

        // Send request to mojang to get the skin values
        val skins = new HashSet<String>();
        val allPlayerDatas = Main.readFile("playerdatas.txt");
        allPlayerDatas.forEach(pd -> {
            val uuid = pd.split("\\|")[0];
            System.out.println("Get skin of " + uuid + " (" + (allPlayerDatas.indexOf(pd) + 1) + "/" + allPlayerDatas.size() + ")");
            //System.out.println(Generators.timeBasedGenerator().generate().toString());
            try {
                val content = getContent("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
                skins.add(gson.fromJson(content, Data.class).getProperties().get(0).getValue());
            } catch (Exception ignored) {
            }
        });

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
    }

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
        if(!file.exists())
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