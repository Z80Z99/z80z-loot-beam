package cn.blockforge.lootbeamslite.client;

import cn.blockforge.lootbeamslite.config.BeamStyle;
import cn.blockforge.lootbeamslite.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration screen.
 *
 * <p>Every label, value and tooltip is a translation key, so the screen follows the language of
 * the client instead of being fixed to one language. The keys are derived from the option id: the
 * label lives in {@code lootbeamslite.config.option.<id>} and its explanation in
 * {@code lootbeamslite.config.option.<id>.tip}. Page titles, value words and the beam style names
 * have their own keys.</p>
 */
public final class ConfigScreen extends Screen {
    private static final String OPTION = "lootbeamslite.config.option.";
    private static final String TIP = ".tip";
    private static final String ON = "lootbeamslite.config.value.on";
    private static final String OFF = "lootbeamslite.config.value.off";
    private static final String TOGGLE_FORMAT = "lootbeamslite.config.format.toggle";
    private static final String VALUE_FORMAT = "lootbeamslite.config.format.value";
    private static final String STYLE = "lootbeamslite.config.style.";

    private final Screen parent;
    private Page page = Page.BEAM;
    private final List<AbstractWidget> rows = new ArrayList<>();
    private int panelLeft, panelRight, contentLeft, contentWidth;
    private int scrollOffset;
    private int listTop;
    private int listBottom;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("lootbeamslite.config.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        clearWidgets(); rows.clear();
        panelLeft = Math.max(8, width / 2 - 310);
        panelRight = Math.min(width - 8, width / 2 + 310);
        int tabWidth = Math.min(128, Math.max(92, (panelRight - panelLeft) / 4));
        contentLeft = panelLeft + tabWidth + 18;
        contentWidth = Math.max(150, panelRight - contentLeft - 12);
        listTop = 62;
        listBottom = Math.max(listTop + 22, height - 48);
        for (Page p : Page.values()) {
            Component label = page == p ? Component.literal("\u25b6 ").append(p.title()) : p.title();
            Button tab = Button.builder(label, b -> {
                        page = p;
                        scrollOffset = 0;
                        refreshWidgets();
                    })
                    .bounds(panelLeft + 8, 62 + p.ordinal() * 28, tabWidth - 16, 22).build();
            tab.setTooltip(Tooltip.create(p.help()));
            addRenderableWidget(tab);
        }
        switch (page) {
            case BEAM -> {
                toggle("enableBeam", ClientConfig.ENABLE_BEAM);
                choice("style", ClientConfig.BEAM_STYLE);
                toggle("enableGlow", ClientConfig.ENABLE_GLOW);
                toggle("commonShorterBeam", ClientConfig.COMMON_SHORTER_BEAM);
                toggle("requireOnGround", ClientConfig.REQUIRE_ON_GROUND);
                toggle("enableDynamic", ClientConfig.ENABLE_DYNAMIC);
                number("beamHeight", ClientConfig.BEAM_HEIGHT, 0, 10);
                number("beamRadius", ClientConfig.BEAM_RADIUS, 0.01, 5);
                number("beamAlpha", ClientConfig.BEAM_ALPHA, 0, 1);
                number("glowRadius", ClientConfig.GLOW_RADIUS, 0.00001, 1);
                number("beamYOffset", ClientConfig.BEAM_Y_OFFSET, -30, 30);
            }
            case FILTER -> {
                toggle("allItems", ClientConfig.ALL_ITEMS);
                toggle("onlyRare", ClientConfig.ONLY_RARE);
                toggle("onlyEquipment", ClientConfig.ONLY_EQUIPMENT);
                toggle("enableCustomColor", ClientConfig.ENABLE_CUSTOM_COLOR);
                number("maxDistance", ClientConfig.MAX_DISTANCE, 8, 256);
                integer("rareOrdinalMinimum", ClientConfig.RARE_ORDINAL_MIN, 0, 16);
            }
            case INFO -> {
                toggle("showName", ClientConfig.SHOW_NAME);
                toggle("nameOnLook", ClientConfig.NAME_ON_LOOK);
                toggle("textBorder", ClientConfig.TEXT_BORDER);
                toggle("stackCount", ClientConfig.STACK_COUNT);
                toggle("crouchTooltips", ClientConfig.CROUCH_TOOLTIPS);
                number("nameDistance", ClientConfig.NAME_DISTANCE, 2, 64);
                number("nameScale", ClientConfig.NAME_SCALE, 0.1, 5);
                number("nameYOffset", ClientConfig.NAME_Y_OFFSET, -5, 10);
                number("nameBackgroundAlpha", ClientConfig.NAME_BACKGROUND_ALPHA, 0, 1);
            }
            case SOUND -> {
                toggle("enableSound", ClientConfig.ENABLE_SOUND);
                number("soundVolume", ClientConfig.SOUND_VOLUME, 0, 1);
                integer("fadeInTicks", ClientConfig.FADE_IN_TICKS, 1, 100);
                number("fadeInDistance", ClientConfig.FADE_IN_DISTANCE, 0, 100);
                integer("halfRoundTicks", ClientConfig.HALF_ROUND_TICKS, 1, 400);
            }
        }
        scrollOffset = Math.min(scrollOffset, maxScroll());
        layoutRows();
        addRenderableWidget(Button.builder(Component.translatable("lootbeamslite.config.button.save"), b -> onClose())
                .bounds(Math.max(contentLeft, panelRight - 120), height - 32, Math.min(112, contentWidth), 20).build());
    }

    private void refreshWidgets() { init(minecraft, width, height); }

    private void toggle(String id, ForgeConfigSpec.BooleanValue value) {
        Button button = Button.builder(toggleText(id, value.get()), b -> {
            value.set(!value.get()); b.setMessage(toggleText(id, value.get()));
        }).bounds(0, 0, contentWidth, 22).build();
        addExplanation(button, id);
        rows.add(button); addRenderableWidget(button);
    }

    private void number(String id, ForgeConfigSpec.DoubleValue config, double min, double max) {
        SettingSlider slider = new SettingSlider(0, 0, contentWidth, 22, id, config.get(), min, max, config::set, false);
        addExplanation(slider, id);
        rows.add(slider); addRenderableWidget(slider);
    }

    private void integer(String id, ForgeConfigSpec.IntValue config, int min, int max) {
        SettingSlider slider = new SettingSlider(0, 0, contentWidth, 22, id, config.get(), min, max,
                v -> config.set((int) Math.round(v)), true);
        addExplanation(slider, id);
        rows.add(slider); addRenderableWidget(slider);
    }

    /** Cyclic picker for a small set of named choices. */
    private void choice(String id, ForgeConfigSpec.EnumValue<BeamStyle> config) {
        Button button = Button.builder(choiceText(id), b -> {
            BeamStyle[] values = BeamStyle.values();
            config.set(values[(config.get().ordinal() + 1) % values.length]);
            b.setMessage(choiceText(id));
        }).bounds(0, 0, contentWidth, 22).build();
        addExplanation(button, id);
        rows.add(button); addRenderableWidget(button);
    }

    private void layoutRows() {
        int y = listTop - scrollOffset;
        for (AbstractWidget widget : rows) {
            widget.setPosition(contentLeft, y);
            widget.setWidth(contentWidth);
            widget.visible = y >= listTop && y + widget.getHeight() <= listBottom;
            y += 28;
        }
    }

    private int maxScroll() {
        return Math.max(0, rows.size() * 28 - (listBottom - listTop));
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= contentLeft && mouseX <= panelRight && mouseY >= listTop && mouseY <= listBottom && maxScroll() > 0) {
            int oldOffset = scrollOffset;
            scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - (int) Math.signum(delta) * 28));
            if (scrollOffset != oldOffset) {
                layoutRows();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private Component toggleText(String id, boolean value) {
        return Component.translatable(TOGGLE_FORMAT, label(id),
                Component.translatable(value ? ON : OFF));
    }

    private Component choiceText(String id) {
        return Component.translatable(VALUE_FORMAT, label(id),
                Component.translatable(STYLE + ClientConfig.BEAM_STYLE.get().name()));
    }

    private Component label(String id) {
        return Component.translatable(OPTION + id);
    }

    private void addExplanation(AbstractWidget widget, String id) {
        widget.setTooltip(Tooltip.create(Component.translatable(OPTION + id + TIP)));
    }

    @Override public void onClose() {
        ClientConfig.SPEC.save();
        // Rules, colours and names depend on the configuration, so the per item memo has to go.
        ItemCache.clear();
        minecraft.setScreen(parent);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(panelLeft, 18, panelRight, height - 8, 0xD8141A20);
        graphics.fill(panelLeft, 18, panelRight, 50, 0xEE202A35);
        graphics.fill(contentLeft - 10, 50, contentLeft - 8, height - 8, 0x663F5266);
        graphics.fill(contentLeft, listBottom + 3, panelRight - 8, listBottom + 4, 0x553F5266);
        graphics.drawString(font, title, panelLeft + 14, 29, 0xFFF4F7FA, false);
        graphics.drawString(font, page.title(), contentLeft, 29, 0xFF78C7FF, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        Component footer = Component.translatable(maxScroll() > 0
                ? "lootbeamslite.config.hint.scroll"
                : "lootbeamslite.config.hint.hover");
        graphics.drawString(font, footer, contentLeft, height - 27, 0xFF9CAAB8, false);
        if (maxScroll() > 0) {
            int trackX = panelRight - 6;
            int trackHeight = listBottom - listTop;
            int thumbHeight = Math.max(14, trackHeight * trackHeight / (rows.size() * 28));
            int thumbY = listTop + (trackHeight - thumbHeight) * scrollOffset / maxScroll();
            graphics.fill(trackX, listTop, trackX + 2, listBottom, 0x553F5266);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFF78C7FF);
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    private enum Page {
        BEAM("lootbeamslite.config.page.beam.title", "lootbeamslite.config.page.beam.help"),
        FILTER("lootbeamslite.config.page.filter.title", "lootbeamslite.config.page.filter.help"),
        INFO("lootbeamslite.config.page.info.title", "lootbeamslite.config.page.info.help"),
        SOUND("lootbeamslite.config.page.sound.title", "lootbeamslite.config.page.sound.help");

        private final String titleKey, helpKey;

        Page(String titleKey, String helpKey) { this.titleKey = titleKey; this.helpKey = helpKey; }

        Component title() { return Component.translatable(titleKey); }

        Component help() { return Component.translatable(helpKey); }
    }

    private static final class SettingSlider extends AbstractSliderButton {
        private final String id;
        private final double min, max;
        private final java.util.function.DoubleConsumer setter;
        private final boolean integer;

        SettingSlider(int x, int y, int width, int height, String id, double current, double min, double max,
                      java.util.function.DoubleConsumer setter, boolean integer) {
            super(x, y, width, height, Component.empty(), (current - min) / (max - min));
            this.id = id; this.min = min; this.max = max; this.setter = setter; this.integer = integer;
            updateMessage();
        }

        private double actual() { double result = min + value * (max - min); return integer ? Math.round(result) : result; }

        @Override protected void updateMessage() {
            double v = actual();
            String text = integer ? Integer.toString((int) v) : String.format("%.2f", v);
            setMessage(Component.translatable(VALUE_FORMAT, Component.translatable(OPTION + id), text));
        }

        @Override protected void applyValue() { setter.accept(actual()); }
    }
}
