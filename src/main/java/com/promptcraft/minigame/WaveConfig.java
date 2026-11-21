package com.promptcraft.minigame;

import java.util.ArrayList;
import java.util.List;

public class WaveConfig {
    public int waveNumber;
    public String waveTitle = "Wave";
    public List<MobSpawnInfo> mobs = new ArrayList<>();

    public static class MobSpawnInfo {
        public String entityId = "minecraft:zombie";
        public int count = 5;
        public float healthMultiplier = 1.0f;
        public String mainHandItem = "";
        public String armorSet = ""; // "iron", "diamond", etc.
    }
}
