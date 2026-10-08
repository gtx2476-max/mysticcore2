package ru.mysticcore.spheres;

import org.bukkit.Material;

import java.util.List;

/**
 * Типы сфер. Крафт (кроме EMPTY):  C E C / E S E / C E C  (S = пустая сфера).
 * EMPTY = 9 голов игроков. В лоре {путь} подставляется из config.yml.
 */
public enum SphereType {
    EMPTY("empty", "&fПустая сфера", "MHF_Question", Material.PLAYER_HEAD, Material.PLAYER_HEAD,
            "&7Основа для всех сфер.", "", "&7Крафт: &f9 голов игроков", "&8Головы выпадают при убийстве игрока"),

    FUGU("fugu", "&aСфера Фугу", "MHF_Slime", Material.SPIDER_EYE, Material.PUFFERFISH,
            "&7В руке: вокруг тебя зелёный круг", "&7радиусом &f{fugu.radius} &7блока.", "",
            "&aВраги в круге получают:", "&c✘ Отравление {fugu.poison-level}", "&c✘ Замедление {fugu.slow-level}", "",
            "&aТебе:", "&a✔ Броня +{fugu.armor-bonus}"),

    POSEIDON("poseidon", "&bСфера Посейдона", "MHF_Squid", Material.NAUTILUS_SHELL, Material.PRISMARINE_CRYSTALS,
            "&7Удар: &f{poseidon.chance}% &7шанс замедлить врага", "",
            "&aТебе:", "&a✔ Подводное дыхание и грация дельфина", "&a✔ Скорость в воде x{poseidon.swim-speed}",
            "&a✔ Броня +{poseidon.armor-bonus}", "&c✘ Здоровье -{poseidon.health-loss}"),

    HADES("hades", "&cСфера Аида", "MHF_LavaSlime", Material.BLAZE_POWDER, Material.MAGMA_BLOCK,
            "&7Удары и стрелы поджигают врагов", "&7Удар: &f{hades.chance}% &7шанс — большой фаербол", "",
            "&aТебе:", "&a✔ Скорость {hades.speed-level}", "&a✔ Огнеупорность", "&a✔ Урон +{hades.damage-bonus}",
            "&c✘ Броня -{hades.armor-loss}"),

    ZEUS("zeus", "&eСфера Зевса", "MHF_Ghast", Material.GOLD_INGOT, Material.LIGHTNING_ROD,
            "&7Удар: &f{zeus.chance}% &7шанс — настоящая молния", "&7бьёт во врага (+урон от молнии)", "",
            "&aТебе:", "&a✔ Спешка {zeus.haste-level}", "&a✔ Скорость атаки +{zeus.attack-speed}",
            "&c✘ Броня -{zeus.armor-loss}"),

    HERMES("hermes", "&bСфера Гермеса", "MHF_Chicken", Material.SUGAR, Material.FEATHER,
            "&7В руке:", "&a✔ Скорость {hermes.speed-level}", "&a✔ Прыгучесть {hermes.jump-level}",
            "&a✔ Безопасное падение +{hermes.safe-fall} блоков", "&c✘ Здоровье -{hermes.health-loss}"),

    ATHENA("athena", "&9Сфера Афины", "MHF_Golem", Material.DIAMOND, Material.IRON_BLOCK,
            "&7В руке:", "&a✔ Сопротивление урону {athena.resistance-level}", "&a✔ Твёрдость брони +{athena.toughness}",
            "&a✔ Сопротивление отбрасыванию", "&c✘ Урон -{athena.attack-loss}"),

    ARES("ares", "&4Сфера Ареса", "MHF_Blaze", Material.REDSTONE_BLOCK, Material.IRON_SWORD,
            "&7Удар: &f{ares.chance}% &7шанс — Иссушение и свечение врага", "",
            "&aТебе:", "&a✔ Урон +{ares.damage-bonus}", "&a✔ Скорость атаки +{ares.attack-speed}",
            "&c✘ Здоровье -{ares.health-loss}"),

    APOLLO("apollo", "&6Сфера Аполлона", "MHF_Villager", Material.GLISTERING_MELON_SLICE, Material.GOLDEN_APPLE,
            "&7Удар: &f{apollo.chance}% &7шанс — лечит тебя на &f{apollo.heal} &7HP", "",
            "&aТебе:", "&a✔ Регенерация {apollo.regen-level}", "&a✔ Удача +{apollo.luck}",
            "&c✘ Урон -{apollo.attack-loss}");

    public final String id;
    public final String displayName;
    public final String fallbackHead;
    public final Material corner;
    public final Material edge;
    public final List<String> lore;

    SphereType(String id, String displayName, String fallbackHead, Material corner, Material edge, String... lore) {
        this.id = id;
        this.displayName = displayName;
        this.fallbackHead = fallbackHead;
        this.corner = corner;
        this.edge = edge;
        this.lore = List.of(lore);
    }

    public static SphereType byId(String id) {
        for (SphereType t : values()) if (t.id.equalsIgnoreCase(id) || t.name().equalsIgnoreCase(id)) return t;
        return null;
    }
}
