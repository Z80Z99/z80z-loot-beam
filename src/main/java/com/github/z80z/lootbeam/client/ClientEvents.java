package com.github.z80z.lootbeam.client;

import com.github.z80z.lootbeam.Z80ZLootBeam;
import com.github.z80z.lootbeam.config.ClientConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;

public final class ClientEvents {
    private static final KeyMapping TOGGLE = new KeyMapping(
            "key.z80z_loot_beam.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.z80z_loot_beam"
    );
    private static boolean sessionEnabled = true;

    /** Ticks between rebuilds of the crouch tooltip text. */
    private static final int TOOLTIP_REFRESH_TICKS = 10;
    private static ItemStack tooltipStack = ItemStack.EMPTY;
    private static List<Component> tooltipLines = List.of();
    private static int tooltipAge = TOOLTIP_REFRESH_TICKS;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            if (ClientConfig.ENABLE_SOUND.get() && mc.player != null) {
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof ItemEntity item && item.tickCount == 1
                            && ItemCache.shouldRender(item) && ItemCache.soundAllowed(item)) {
                        mc.level.playLocalSound(item.getX(), item.getY(), item.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                                SoundSource.PLAYERS, ClientConfig.SOUND_VOLUME.get().floatValue(), 1.15f, false);
                    }
                }
            }
            updateCrouchTooltip(mc);
        } else {
            resetCrouchTooltip();
        }
        while (TOGGLE.consumeClick()) {
            sessionEnabled = !sessionEnabled;
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable(sessionEnabled
                        ? "message.z80z_loot_beam.enabled"
                        : "message.z80z_loot_beam.disabled"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!sessionEnabled || !ClientConfig.ENABLED.get()) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        // One pass over the world: the beams are submitted before the names, which keeps the
        // text on top of the columns, and the frustum from the event drops off screen items.
        LootBeamRenderer.render(event.getPoseStack(), event.getPartialTick(), event.getFrustum());
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        // RenderGuiEvent fires once per frame, after every vanilla overlay has been
        // drawn, so the cached lines are blitted exactly once and end up on top.
        if (tooltipLines.isEmpty() || !sessionEnabled || !ClientConfig.CROUCH_TOOLTIPS.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isCrouching()) return;
        int x = ClientConfig.TOOLTIP_X.get();
        int y = event.getWindow().getGuiScaledHeight() - ClientConfig.TOOLTIP_Y.get();
        event.getGuiGraphics().renderTooltip(mc.font, tooltipLines, Optional.empty(), x, y);
    }

    /**
     * Finds the item the crouching player is looking at and builds its tooltip text.
     * Doing this once per client tick keeps entity scans, item rule lookups and the
     * (expensive) tooltip line construction out of the frame render path.
     */
    private static void updateCrouchTooltip(Minecraft mc) {
        if (!sessionEnabled || !ClientConfig.CROUCH_TOOLTIPS.get() || mc.level == null
                || mc.player == null || !mc.player.isCrouching()) {
            resetCrouchTooltip();
            return;
        }
        ItemEntity best = null;
        double distance = ClientConfig.NAME_DISTANCE.get();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ItemEntity item && ItemCache.shouldRender(item) && ItemRules.isLookedAt(item)) {
                double d = mc.player.distanceTo(item);
                if (d < distance) { distance = d; best = item; }
            }
        }
        if (best == null) {
            resetCrouchTooltip();
            return;
        }
        ItemStack stack = best.getItem();
        if (!tooltipStack.isEmpty() && tooltipAge < TOOLTIP_REFRESH_TICKS && ItemStack.matches(tooltipStack, stack)) {
            tooltipAge++;
            return;
        }
        tooltipStack = stack.copy();
        tooltipLines = stack.getTooltipLines(mc.player, mc.options.advancedItemTooltips
                ? TooltipFlag.Default.ADVANCED
                : TooltipFlag.Default.NORMAL);
        tooltipAge = 0;
    }

    private static void resetCrouchTooltip() {
        if (tooltipStack.isEmpty() && tooltipLines.isEmpty()) return;
        tooltipStack = ItemStack.EMPTY;
        tooltipLines = List.of();
        tooltipAge = TOOLTIP_REFRESH_TICKS;
    }

    @Mod.EventBusSubscriber(modid = Z80ZLootBeam.MOD_ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBusEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE);
        }
    }

    private ClientEvents() {}
}
