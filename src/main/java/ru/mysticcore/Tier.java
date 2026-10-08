package ru.mysticcore;

import org.bukkit.Material;

/** Уровень мистического сундука / аирдропа. */
public record Tier(String key, String name, long price, int unlockSeconds, Material block, Material icon, String desc) {
}
