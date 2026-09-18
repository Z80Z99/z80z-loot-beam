package com.github.z80z.lootbeam.client;

import com.github.z80z.lootbeam.config.ClientConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

public final class ItemRules {
    /** Parsed tag keys, so a tag entry is only turned into a key once. */
    private static final Map<String, TagKey<Item>> TAG_CACHE = new HashMap<>();

    public static boolean shouldRender(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String idText = id.toString();
        if (matches(id, idText, stack, ClientConfig.BLACKLIST_NAMES.get(), ClientConfig.BLACKLIST_TAGS.get(), ClientConfig.BLACKLIST_MODS.get())) return false;
        if (matches(id, idText, stack, ClientConfig.WHITELIST_NAMES.get(), ClientConfig.WHITELIST_TAGS.get(), ClientConfig.WHITELIST_MODS.get())) return true;
        if (ClientConfig.ALL_ITEMS.get()) return true;
        if (ClientConfig.ONLY_RARE.get() && rarityOrdinal(stack) < ClientConfig.RARE_ORDINAL_MIN.get()) return false;
        return !ClientConfig.ONLY_EQUIPMENT.get() || isEquipment(stack, id, idText);
    }

    public static boolean soundAllowed(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return !matches(id, id.toString(), stack, ClientConfig.SOUND_BLACKLIST_NAMES.get(),
                ClientConfig.SOUND_BLACKLIST_TAGS.get(), ClientConfig.SOUND_BLACKLIST_MODS.get());
    }

    public static int color(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (ClientConfig.ENABLE_CUSTOM_COLOR.get()) {
            Integer value = colorEntry(ClientConfig.COLOR_BY_NAME.get(), id.toString());
            if (value != null) return value;
            for (String entry : ClientConfig.COLOR_BY_TAG.get()) {
                int split = entry.lastIndexOf('=');
                if (split > 0 && hasTag(stack, entry.substring(0, split))) return parseColor(entry.substring(split + 1));
            }
            value = colorEntry(ClientConfig.COLOR_BY_MOD.get(), id.getNamespace());
            if (value != null) return value;
        }
        if (stack.isEnchanted()) return 0xB05CFF;
        if (ClientConfig.USE_NAME_COLOR.get()) {
            var hoverColor = stack.getHoverName().getStyle().getColor();
            if (hoverColor != null) return hoverColor.getValue();
        }
        return switch (stack.getRarity()) {
            case UNCOMMON -> 0x55FF55;
            case RARE -> 0x55AAFF;
            case EPIC -> 0xFF55FF;
            default -> 0xFFFFFF;
        };
    }

    public static boolean isLookedAt(net.minecraft.world.entity.item.ItemEntity item) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return false;
        var eye = mc.player.getEyePosition();
        var target = item.getBoundingBox().getCenter().subtract(eye).normalize();
        double dot = mc.player.getViewVector(1).dot(target);
        return 1.0 - dot <= ClientConfig.LOOK_SENSITIVITY.get();
    }

    private static boolean isEquipment(ItemStack stack, ResourceLocation id, String idText) {
        if (ClientConfig.EQUIPMENT_BLACKLIST.get().contains(idText)) return false;
        if (stack.getItem() instanceof TieredItem || stack.getItem() instanceof ArmorItem ||
                stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem ||
                stack.getItem() instanceof TridentItem || stack.isDamageableItem()) return true;
        return matches(id, idText, stack, ClientConfig.EQUIPMENT_NAMES.get(), ClientConfig.EQUIPMENT_TAGS.get(), ClientConfig.EQUIPMENT_MODS.get());
    }

    private static boolean matches(ResourceLocation id, String idText, ItemStack stack, List<? extends String> names,
                                   List<? extends String> tags, List<? extends String> mods) {
        if (names.contains(idText) || mods.contains(id.getNamespace())) return true;
        for (String tag : tags) if (hasTag(stack, tag)) return true;
        return false;
    }

    private static boolean hasTag(ItemStack stack, String text) {
        TagKey<Item> key = TAG_CACHE.computeIfAbsent(text, entry -> {
            String clean = entry.startsWith("#") ? entry.substring(1) : entry;
            ResourceLocation id = ResourceLocation.tryParse(clean);
            return id == null ? null : TagKey.create(net.minecraft.core.registries.Registries.ITEM, id);
        });
        return key != null && stack.is(key);
    }

    private static int rarityOrdinal(ItemStack stack) { return stack.getRarity().ordinal(); }
    private static Integer colorEntry(List<? extends String> entries, String key) {
        for (String entry : entries) {
            int split = entry.lastIndexOf('=');
            if (split > 0 && entry.substring(0, split).equals(key)) return parseColor(entry.substring(split + 1));
        }
        return null;
    }
    private static int parseColor(String text) {
        try { return Integer.parseInt(text.trim().replace("#", ""), 16) & 0xFFFFFF; }
        catch (NumberFormatException ignored) { return 0xFFFFFF; }
    }
    private ItemRules() {}
}
