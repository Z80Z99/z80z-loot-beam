package com.github.z80z.lootbeam.client;

import com.github.z80z.lootbeam.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per dropped item memo for everything the mod asks for over and over.
 *
 * <p>The beam pass, the name pass and the tick handlers all want the same answers about the same
 * item, every frame, and those answers are not free: they build registry keys, item names and tag
 * keys. Every call used to redo that work. This cache keeps one entry per dropped item and
 * recomputes it only when the stack behind the item actually changed.</p>
 *
 * <p>Entries are held weakly, so removed items are collected with the entity, and
 * {@link #clear()} is called whenever the player changes the configuration.</p>
 */
public final class ItemCache {
    private static final Map<ItemEntity, Entry> ENTRIES = new WeakHashMap<>();

    private static final class Entry {
        ItemStack stack = ItemStack.EMPTY;
        boolean render;
        boolean sound;
        int color;
        Component name = Component.empty();
        int nameWidth;
    }

    /** Whether the item passes the filter rules. */
    public static boolean shouldRender(ItemEntity item) {
        return entry(item).render;
    }

    /** Whether the item may play the spawn sound. */
    public static boolean soundAllowed(ItemEntity item) {
        return entry(item).sound;
    }

    /** Colour the beam should use, already resolved from custom colours and rarity. */
    public static int color(ItemEntity item) {
        return entry(item).color;
    }

    /** Name to draw above the item, including the stack count when that is enabled. */
    public static Component name(ItemEntity item) {
        return entry(item).name;
    }

    /** Width of {@link #name(ItemEntity)} at scale 1, so the render pass does not re-measure it. */
    public static int nameWidth(ItemEntity item) {
        return entry(item).nameWidth;
    }

    /** Drops every memo; call this after configuration changes. */
    public static void clear() {
        ENTRIES.clear();
    }

    private static Entry entry(ItemEntity item) {
        ItemStack stack = item.getItem();
        Entry entry = ENTRIES.get(item);
        if (entry != null && ItemStack.matches(entry.stack, stack)) return entry;
        if (entry == null) {
            entry = new Entry();
            ENTRIES.put(item, entry);
        }
        entry.stack = stack.copy();
        entry.render = ItemRules.shouldRender(stack);
        entry.sound = ItemRules.soundAllowed(stack);
        entry.color = ItemRules.color(stack);
        entry.name = ClientConfig.STACK_COUNT.get() && stack.getCount() > 1
                ? Component.literal(stack.getCount() + "\u00d7 ").append(stack.getHoverName())
                : stack.getHoverName();
        entry.nameWidth = Minecraft.getInstance().font.width(entry.name);
        return entry;
    }

    private ItemCache() {}
}
