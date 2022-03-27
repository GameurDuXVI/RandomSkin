package fr.gameurduxvi.randomskin.mcapi;

import lombok.Data;

import java.util.List;

@Data
public class PlayersData {
    private int max;
    private int now;
    private List<PlayerData> sample;
}
