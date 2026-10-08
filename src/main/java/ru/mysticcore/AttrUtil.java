package ru.mysticcore;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

/** Накладывает/снимает временные (transient) модификаторы атрибутов. */
public final class AttrUtil {
    private AttrUtil() {}

    public static void apply(MysticCore plugin, Player p, String keyName, Attribute attr,
                             double amount, AttributeModifier.Operation op, boolean active) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        NamespacedKey k = new NamespacedKey(plugin, keyName);
        AttributeModifier current = null;
        for (AttributeModifier am : inst.getModifiers()) {
            if (k.equals(am.getKey())) { current = am; break; }
        }
        if (active) {
            if (current != null && current.getAmount() == amount) return;
            if (current != null) inst.removeModifier(current);
            inst.addTransientModifier(new AttributeModifier(k, amount, op, EquipmentSlotGroup.ANY));
        } else if (current != null) {
            inst.removeModifier(current);
        }
    }
}
