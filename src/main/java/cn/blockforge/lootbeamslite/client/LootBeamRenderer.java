package cn.blockforge.lootbeamslite.client;

import cn.blockforge.lootbeamslite.config.ClientConfig;
import cn.blockforge.lootbeamslite.config.BeamStyle;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Loot beam renderer.
 *
 * <p>The beam is a real cylinder made of radial segments and concentric shells.
 * Without a shader pack it is drawn with the position_color shader into the main
 * render target using additive blending. That shader applies no linear fog, so the
 * beam stays bright instead of fading out with distance, and the geometry is a
 * volume, not a flat cross.</p>
 *
 * <p>While a shader pack is active it replaces every world shader, so the additive
 * position_color path lands in a program written for blocks and comes back washed
 * out. The shader path below draws the exact same geometry, colours and blending,
 * but through a vanilla shader that no pack overrides, so the beam looks the same
 * with and without a pack.</p>
 */
public final class LootBeamRenderer {
    /** Radial segments of the cylinder cross section. */
    private static final int RADIAL_SEGMENTS = 20;
    /** Radial segments of the shader path; it shades a whole panel with one colour. */
    private static final int SHADER_RADIAL_SEGMENTS = 64;
    /** Quads along a spiral ribbon. */
    private static final int RIBBON_STEPS = 44;
    /** Rings drawn by the {@link BeamStyle#RINGS} style. */
    private static final int RING_COUNT = 7;
    /** Segments of one ring. */
    private static final int RING_SEGMENTS = 28;
    /** Vertical bands, used to smooth the bottom to top gradient. */
    private static final int VERTICAL_BANDS = 4;
    /** Shell radii relative to the configured beam radius: core, body, halo. */
    private static final float[] SHELL_RADIUS = {0.30f, 0.62f, 1.00f};
    /** Per shell brightness weight. */
    private static final float[] SHELL_ALPHA = {0.46f, 0.20f, 0.09f};
    /** Per shell whiteness, so the core looks hotter than the halo. */
    private static final float[] SHELL_WHITENESS = {0.55f, 0.20f, 0.02f};

    /** Shell setup of one style: radii, brightness weights, whiteness and the upward taper. */
    private record Shells(float[] radii, float[] alpha, float[] whiteness, float taper) {}

    private static final Shells CLASSIC_SHELLS = new Shells(SHELL_RADIUS, SHELL_ALPHA, SHELL_WHITENESS, 0.0f);
    private static final Shells LASER_SHELLS = new Shells(
            new float[]{0.14f, 0.36f}, new float[]{0.85f, 0.30f}, new float[]{0.30f, 0.04f}, 0.0f);
    private static final Shells SOFT_SHELLS = new Shells(
            new float[]{0.60f, 0.84f, 1.00f}, new float[]{0.30f, 0.15f, 0.07f},
            new float[]{0.28f, 0.10f, 0.02f}, 0.0f);
    private static final Shells CONE_SHELLS = new Shells(
            new float[]{0.26f, 0.56f, 1.00f}, new float[]{0.50f, 0.22f, 0.10f},
            new float[]{0.55f, 0.20f, 0.02f}, 0.82f);

    private static Shells shells(BeamStyle style) {
        return switch (style) {
            case LASER -> LASER_SHELLS;
            case SOFT -> SOFT_SHELLS;
            case CONE -> CONE_SHELLS;
            default -> CLASSIC_SHELLS;
        };
    }
    /**
     * Additive blending: the beam adds light to the scene instead of covering it. Both paths
     * share it, so the pack only changes how a beam is lit, not how it is blended.
     */
    private static final RenderStateShard.TransparencyStateShard ADDITIVE_TRANSPARENCY =
            new RenderStateShard.TransparencyStateShard(
                    "z80z_loot_beam_additive",
                    () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                    },
                    () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    });

    private static final RenderType BEAM_TYPE = RenderType.create(
            "z80z_loot_beam_beam",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            // Additive blending is order independent, so the quads never need sorting on upload.
            1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setDepthTestState(new RenderStateShard.DepthTestStateShard("lequal", 515))
                    .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, false))
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .setLightmapState(new RenderStateShard.LightmapStateShard(false))
                    .setOverlayState(new RenderStateShard.OverlayStateShard(false))
                    .createCompositeState(false));

    /**
     * Gradient textures of the shader path. The particle program takes the colour of a single
     * corner per quad, so a per vertex gradient would be quantised into banded stripes, while
     * texture coordinates are interpolated smoothly by every program. The beam gradient holds
     * the fade towards the top, the glow gradient the falloff of the ground glow and its ring.
     */
    private static final ResourceLocation BEAM_GRADIENT =
            new ResourceLocation("lootbeamslite", "textures/entity/beam_gradient.png");
    private static final ResourceLocation GLOW_GRADIENT =
            new ResourceLocation("lootbeamslite", "textures/entity/glow_gradient.png");

    /**
     * Shader pack variant of {@link #BEAM_TYPE}.
     *
     * <p>A pack replaces every world shader, so the plain position_color path lands in a program
     * written for blocks: it lights and squares the vertex colour, which bleached the beam, and
     * it throws away fragments whose alpha is under 0.5, which produced the dithered stripes.</p>
     *
     * <p>The specialised programs are no better for a beam. The beacon beam program squares the
     * colour even harder and hands the result to the lighting stage, which turns the beam black.
     * The lightning program replaces the colour outright with its own material colour and forces
     * the alpha to one, which turns the beam into a solid tube that no longer fades with distance
     * or towards its top. Going around the pack entirely does not work either: a pack paints over
     * every pixel that was not marked in its own buffers, so a beam drawn with a vanilla shader
     * is simply erased by the sky.</p>
     *
     * <p>The particle program is the one the packs build for exactly this kind of thing, so the
     * shader path uses it and works around its two quirks: it takes the colour of a single corner
     * per quad, and it rewrites certain colours as smoke, snow or lava particles. Therefore every
     * panel is drawn with one flat colour and the visible gradients live in a gradient texture,
     * whose coordinates every program interpolates smoothly, while the texture colour is kept
     * just off pure white and the vertex alpha at one so the particle detectors stay quiet.</p>
     */
    private static final RenderType SHADER_BEAM_TYPE =
            shaderType("z80z_loot_beam_beam_particle", BEAM_GRADIENT);
    /** Shader pack variant of the ground glow: same program, the glow gradient as its texture. */
    private static final RenderType SHADER_GLOW_TYPE =
            shaderType("z80z_loot_beam_glow_particle", GLOW_GRADIENT);

    private static RenderType shaderType(String name, ResourceLocation texture) {
        return RenderType.create(
                name,
                DefaultVertexFormat.PARTICLE,
                VertexFormat.Mode.QUADS,
                1536, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getParticleShader))
                        // Linear filtering: the beam gradient only has 64 steps, and nearest
                        // sampling turns those into visible bands up close.
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setDepthTestState(new RenderStateShard.DepthTestStateShard("lequal", 515))
                        .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, false))
                        .setCullState(new RenderStateShard.CullStateShard(false))
                        .setLightmapState(new RenderStateShard.LightmapStateShard(true))
                        .setOverlayState(new RenderStateShard.OverlayStateShard(false))
                        .createCompositeState(false));
    }

    private static Method irisShaderPackInUse;
    private static Object irisInstance;
    private static boolean irisLookupDone;
    private static boolean irisLastResult;
    private static long irisLastCheck;

    /**
     * True while a shader pack is rewriting the world shaders. Detected through the Iris
     * API so the renderer has no hard dependency on the shader loader. The answer is only
     * refreshed twice per second: packs cannot change mid frame, and this keeps the reflective
     * call out of the hot path.
     */
    private static boolean shaderPackActive() {
        long now = System.nanoTime();
        if (irisLastCheck != 0L && now - irisLastCheck < 500_000_000L) return irisLastResult;
        irisLastCheck = now;
        if (!irisLookupDone) {
            irisLookupDone = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisInstance = api.getMethod("getInstance").invoke(null);
                irisShaderPackInUse = api.getMethod("isShaderPackInUse");
            } catch (Throwable ignored) {
                irisShaderPackInUse = null;
            }
        }
        if (irisShaderPackInUse == null) return irisLastResult = false;
        try {
            irisLastResult = Boolean.TRUE.equals(irisShaderPackInUse.invoke(irisInstance));
        } catch (Throwable ignored) {
            irisShaderPackInUse = null;
            irisLastResult = false;
        }
        return irisLastResult;
    }

    /** Configuration of the current frame, read once instead of once per item per frame. */
    private record Settings(boolean beams, BeamStyle style, float height, boolean shortCommon, float radius,
                            boolean dynamic, int halfRound, float fadeInTicks, float fadeDistance, float alpha,
                            boolean solid, boolean glow, float glowRadius, boolean onGround, boolean shaderMode,
                            double maxDistance, boolean names, boolean namesOnLook, float nameDistance,
                            float nameScale, float nameAlpha, float nameBackground, boolean nameBorder,
                            boolean stackCount, float beamYOffset, float nameYOffset) {}

    /** One visible item of the current frame; the list is reused between frames. */
    private static final class Slot {
        Component name;
        int nameWidth;
        int color;
        float red, green, blue;
        float fade;
        float dirX, dirZ;
        float age;
        double x, y, z;
        boolean common;
        boolean beam;
    }

    private static final List<Slot> SLOTS = new ArrayList<>();
    private static final AABB BOX = new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    private static Slot slot(int index) {
        while (SLOTS.size() <= index) SLOTS.add(new Slot());
        return SLOTS.get(index);
    }

    private static Settings readSettings(boolean shaderMode) {
        return new Settings(
                ClientConfig.ENABLE_BEAM.get(), ClientConfig.BEAM_STYLE.get(),
                ClientConfig.BEAM_HEIGHT.get().floatValue(), ClientConfig.COMMON_SHORTER_BEAM.get(),
                ClientConfig.BEAM_RADIUS.get().floatValue(), ClientConfig.ENABLE_DYNAMIC.get(),
                ClientConfig.HALF_ROUND_TICKS.get(), ClientConfig.FADE_IN_TICKS.get().floatValue(),
                ClientConfig.FADE_IN_DISTANCE.get().floatValue(), ClientConfig.BEAM_ALPHA.get().floatValue(),
                ClientConfig.SOLID_BEAM.get(), ClientConfig.ENABLE_GLOW.get(),
                ClientConfig.GLOW_RADIUS.get().floatValue(), ClientConfig.REQUIRE_ON_GROUND.get(), shaderMode,
                ClientConfig.MAX_DISTANCE.get(), ClientConfig.SHOW_NAME.get(), ClientConfig.NAME_ON_LOOK.get(),
                ClientConfig.NAME_DISTANCE.get().floatValue(), ClientConfig.NAME_SCALE.get().floatValue(),
                ClientConfig.NAME_TEXT_ALPHA.get().floatValue(), ClientConfig.NAME_BACKGROUND_ALPHA.get().floatValue(),
                ClientConfig.TEXT_BORDER.get(), ClientConfig.STACK_COUNT.get(),
                ClientConfig.BEAM_Y_OFFSET.get().floatValue(), ClientConfig.NAME_Y_OFFSET.get().floatValue());
    }

    /**
     * Draws every beam and then every name of this frame.
     *
     * <p>The world is scanned once: the item rules, the colour and the display name come from
     * {@link ItemCache}, the frustum throws away what the player cannot see, and the
     * configuration is read a single time into {@link Settings}. Beams are submitted before the
     * names, so the text still ends up on top of the columns.</p>
     */
    public static void render(PoseStack pose, float partialTick, Frustum frustum) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Settings settings = readSettings(shaderPackActive());
        boolean beams = settings.beams || settings.glow;
        if (!beams && !settings.names) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;
        double beamRange = beams ? settings.maxDistance : 0.0;
        double nameRange = settings.names ? settings.nameDistance : 0.0;
        double rangeSq = Math.max(beamRange, nameRange);
        rangeSq *= rangeSq;

        int count = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity item) || !item.isAlive()) continue;
            if (settings.onGround && !item.onGround()) continue;
            double x = Mth.lerp(partialTick, item.xOld, item.getX()) - camX;
            double y = Mth.lerp(partialTick, item.yOld, item.getY()) - camY;
            double z = Mth.lerp(partialTick, item.zOld, item.getZ()) - camZ;
            double distanceSq = x * x + y * y + z * z;
            if (distanceSq > rangeSq) continue;
            double distance = Math.sqrt(distanceSq);
            boolean drawBeam = beams && distance <= beamRange;
            boolean drawName = settings.names && distance <= nameRange;
            if (!drawBeam && !drawName) continue;
            if (!ItemCache.shouldRender(item)) continue;
            if (!visible(frustum, camX, camY, camZ, x, y, z, settings, drawBeam)) continue;

            Slot slot = slot(count++);
            slot.x = x;
            slot.y = y;
            slot.z = z;
            slot.beam = drawBeam;
            slot.color = ItemCache.color(item);
            slot.red = ((slot.color >> 16) & 255) / 255.0f;
            slot.green = ((slot.color >> 8) & 255) / 255.0f;
            slot.blue = (slot.color & 255) / 255.0f;
            slot.common = item.getItem().getRarity() == Rarity.COMMON;
            slot.age = item.tickCount + partialTick;
            slot.fade = Mth.clamp(item.tickCount / settings.fadeInTicks, 0f, 1f);
            if (settings.fadeDistance > 0.001f) {
                // x and z are camera relative already, so this is the horizontal
                // distance between the camera and the item, measured in blocks.
                slot.fade *= closeRangeFade(Mth.sqrt((float) (x * x + z * z)), settings.fadeDistance);
            }
            // The pose is a pure translation, so local and camera relative axes point
            // the same way. This direction softens the silhouette of the cylinder.
            float dirX = (float) -x;
            float dirZ = (float) -z;
            float dirLength = Mth.sqrt(dirX * dirX + dirZ * dirZ);
            if (dirLength < 1.0E-4f) {
                slot.dirX = 0.0f;
                slot.dirZ = 1.0f;
            } else {
                slot.dirX = dirX / dirLength;
                slot.dirZ = dirZ / dirLength;
            }
            boolean name = drawName && (!settings.namesOnLook || ItemRules.isLookedAt(item));
            slot.name = name ? ItemCache.name(item) : null;
            slot.nameWidth = name ? ItemCache.nameWidth(item) : 0;
        }
        if (count == 0) return;

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        if (beams) {
            VertexConsumer beamBuffer = buffers.getBuffer(settings.shaderMode ? SHADER_BEAM_TYPE : BEAM_TYPE);
            VertexConsumer glowBuffer = settings.shaderMode ? buffers.getBuffer(SHADER_GLOW_TYPE) : beamBuffer;
            for (int i = 0; i < count; i++) {
                Slot slot = SLOTS.get(i);
                if (!slot.beam) continue;
                drawBeam(pose, beamBuffer, glowBuffer, settings, slot);
            }
        }
        if (settings.names) {
            Quaternionf orientation = mc.getEntityRenderDispatcher().cameraOrientation();
            for (int i = 0; i < count; i++) {
                Slot slot = SLOTS.get(i);
                if (slot.name == null) continue;
                drawName(pose, buffers, orientation, mc.font, settings, slot);
            }
        }
        buffers.endBatch();
    }

    /**
     * Cheap frustum test around the box one item occupies. The frustum works in world space, so
     * the camera relative position used by the beam has to be turned back into world coordinates.
     */
    private static boolean visible(Frustum frustum, double camX, double camY, double camZ,
                                   double x, double y, double z, Settings settings, boolean beam) {
        if (frustum == null) return true;
        double radius = Math.max(settings.radius, 0.5f);
        double height = beam ? Math.max(settings.height, 1.0f)
                : Math.max(1.0f, settings.nameYOffset + 1.5f);
        double worldX = camX + x;
        double worldY = camY + y;
        double worldZ = camZ + z;
        BOX.setMinX(worldX - radius);
        BOX.setMinY(worldY);
        BOX.setMinZ(worldZ - radius);
        BOX.setMaxX(worldX + radius);
        BOX.setMaxY(worldY + height);
        BOX.setMaxZ(worldZ + radius);
        return frustum.isVisible(BOX);
    }

    /** 0 at point blank range, smoothly rising to 1 at the configured distance. */
    private static float closeRangeFade(float distance, float range) {
        float t = Mth.clamp(distance / range, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static void drawBeam(PoseStack pose, VertexConsumer out, VertexConsumer glowOut,
                                 Settings settings, Slot slot) {
        float r = slot.red;
        float g = slot.green;
        float b = slot.blue;
        float h = settings.height * (slot.common && settings.shortCommon ? 0.55f : 1f);
        float w = settings.radius;
        boolean dynamic = settings.dynamic;
        float phase = slot.age / Math.max(1, settings.halfRound) * Mth.PI;
        float pulse = dynamic ? 0.88f + 0.12f * Mth.sin(phase) : 1f;
        float alpha = settings.alpha * pulse * slot.fade;
        if (!settings.solid) alpha *= 0.6f;
        float breathe = dynamic ? 1.0f + 0.045f * Mth.sin(phase * 0.8f) : 1.0f;

        pose.pushPose();
        pose.translate(slot.x, slot.y + 0.12 + settings.beamYOffset, slot.z);
        Matrix4f matrix = pose.last().pose();

        if (settings.beams && h > 0.01f) {
            BeamStyle style = settings.style;
            if (style == BeamStyle.SPIRAL || style == BeamStyle.DOUBLE_HELIX) {
                int strands = style == BeamStyle.DOUBLE_HELIX ? 2 : 1;
                for (int strand = 0; strand < strands; strand++) {
                    float offset = strand * Mth.PI;
                    if (settings.shaderMode) {
                        ribbonShader(out, matrix, w * breathe, h, r, g, b, alpha, phase, dynamic, offset);
                    } else {
                        ribbon(out, matrix, w * breathe, h, r, g, b, alpha, phase, dynamic, offset);
                    }
                }
            } else if (style == BeamStyle.RINGS) {
                if (settings.shaderMode) {
                    ringsShader(out, matrix, w * breathe, h, r, g, b, alpha, phase, dynamic);
                } else {
                    rings(out, matrix, w * breathe, h, r, g, b, alpha, phase, dynamic);
                }
            } else {
                Shells shells = shells(style);
                for (int shell = 0; shell < shells.radii().length; shell++) {
                    float sr = Mth.lerp(shells.whiteness()[shell], r, 1.0f);
                    float sg = Mth.lerp(shells.whiteness()[shell], g, 1.0f);
                    float sb = Mth.lerp(shells.whiteness()[shell], b, 1.0f);
                    float radius = w * shells.radii()[shell] * breathe;
                    if (settings.shaderMode) {
                        // One flat colour per panel, alpha stays at one; the pack takes the colour
                        // of a single corner, so a per vertex gradient would only band.
                        float level = alpha * shells.alpha()[shell];
                        cylinderShader(out, matrix, radius, h, sr * level, sg * level, sb * level,
                                shells.taper());
                    } else {
                        cylinder(out, matrix, radius, h, sr, sg, sb,
                                alpha * shells.alpha()[shell], slot.dirX, slot.dirZ, phase, dynamic,
                                shells.taper());
                    }
                }
            }
        }
        if (settings.glow) {
            float radius = settings.glowRadius;
            float ring = radius * (1.08f + 0.06f * Mth.sin(phase));
            if (settings.shaderMode) {
                float level = pulse * slot.fade;
                glowShader(glowOut, matrix, ring, r * level, g * level, b * level);
            } else {
                groundDisc(out, matrix, radius, r, g, b, 0.42f * pulse * slot.fade, phase);
                groundRing(out, matrix, ring, r, g, b, 0.46f * slot.fade, phase);
            }
        }
        pose.popPose();
    }

    /** Draws one closed shell of the cylinder. Culling is off, so both sides show. */
    private static void cylinder(VertexConsumer out, Matrix4f m, float radius, float height,
                                 float r, float g, float b, float alpha,
                                 float dirX, float dirZ, float phase, boolean dynamic, float taper) {
        for (int i = 0; i < RADIAL_SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / RADIAL_SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / RADIAL_SEGMENTS;
            float n0x = Mth.cos(a0);
            float n0z = Mth.sin(a0);
            float n1x = Mth.cos(a1);
            float n1z = Mth.sin(a1);
            float f0 = rimFacing(n0x, n0z, dirX, dirZ);
            float f1 = rimFacing(n1x, n1z, dirX, dirZ);
            for (int j = 0; j < VERTICAL_BANDS; j++) {
                float t0 = (float) j / VERTICAL_BANDS;
                float t1 = (float) (j + 1) / VERTICAL_BANDS;
                float y0 = height * t0;
                float y1 = height * t1;
                float w0 = radius * (1.0f - taper * t0);
                float w1 = radius * (1.0f - taper * t1);
                float p0 = verticalProfile(t0) * shimmer(t0, phase, dynamic) * alpha;
                float p1 = verticalProfile(t1) * shimmer(t1, phase, dynamic) * alpha;
                vertex(out, m, n0x * w0, y0, n0z * w0, r, g, b, p0 * f0);
                vertex(out, m, n1x * w0, y0, n1z * w0, r, g, b, p0 * f1);
                vertex(out, m, n1x * w1, y1, n1z * w1, r, g, b, p1 * f1);
                vertex(out, m, n0x * w1, y1, n0z * w1, r, g, b, p1 * f0);
            }
        }
    }

    /**
     * Shader path shell. The pack shades a whole panel with one colour, so the panel count is
     * raised to keep the silhouette smooth, and the vertical fade comes from the gradient
     * texture instead of from the vertices.
     */
    private static void cylinderShader(VertexConsumer out, Matrix4f m, float radius, float height,
                                       float r, float g, float b, float taper) {
        float bottom = radius;
        float top = radius * (1.0f - taper);
        for (int i = 0; i < SHADER_RADIAL_SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SHADER_RADIAL_SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / SHADER_RADIAL_SEGMENTS;
            float n0x = Mth.cos(a0);
            float n0z = Mth.sin(a0);
            float n1x = Mth.cos(a1);
            float n1z = Mth.sin(a1);
            vertexShader(out, m, n0x * bottom, 0.0f, n0z * bottom, 0.5f, 1.0f, r, g, b);
            vertexShader(out, m, n1x * bottom, 0.0f, n1z * bottom, 0.5f, 1.0f, r, g, b);
            vertexShader(out, m, n1x * top, height, n1z * top, 0.5f, 0.0f, r, g, b);
            vertexShader(out, m, n0x * top, height, n0z * top, 0.5f, 0.0f, r, g, b);
        }
    }

    /**
     * Spiral ribbon: a band that winds around the beam axis while it rises, so the column reads
     * as a turning strip of light.
     */
    private static void ribbon(VertexConsumer out, Matrix4f m, float radius, float height,
                               float r, float g, float b, float alpha, float phase, boolean dynamic,
                               float angleOffset) {
        float turns = Mth.TWO_PI * 1.5f;
        float middle = radius * 0.80f;
        float half = radius * 0.36f;
        for (int i = 0; i < RIBBON_STEPS; i++) {
            float t0 = (float) i / RIBBON_STEPS;
            float t1 = (float) (i + 1) / RIBBON_STEPS;
            float a0 = angleOffset + phase + turns * t0;
            float a1 = angleOffset + phase + turns * t1;
            float p0 = verticalProfile(t0) * shimmer(t0, phase, dynamic) * alpha;
            float p1 = verticalProfile(t1) * shimmer(t1, phase, dynamic) * alpha;
            float c0x = Mth.cos(a0);
            float c0z = Mth.sin(a0);
            float c1x = Mth.cos(a1);
            float c1z = Mth.sin(a1);
            vertex(out, m, c0x * (middle - half), height * t0, c0z * (middle - half), r, g, b, p0);
            vertex(out, m, c0x * (middle + half), height * t0, c0z * (middle + half), r, g, b, p0);
            vertex(out, m, c1x * (middle + half), height * t1, c1z * (middle + half), r, g, b, p1);
            vertex(out, m, c1x * (middle - half), height * t1, c1z * (middle - half), r, g, b, p1);
        }
    }

    /** Shader path ribbon: one flat colour per quad, the vertical fade comes from the texture. */
    private static void ribbonShader(VertexConsumer out, Matrix4f m, float radius, float height,
                                     float r, float g, float b, float alpha, float phase, boolean dynamic,
                                     float angleOffset) {
        float turns = Mth.TWO_PI * 1.5f;
        float middle = radius * 0.80f;
        float half = radius * 0.36f;
        float cr = r * alpha;
        float cg = g * alpha;
        float cb = b * alpha;
        for (int i = 0; i < RIBBON_STEPS; i++) {
            float t0 = (float) i / RIBBON_STEPS;
            float t1 = (float) (i + 1) / RIBBON_STEPS;
            float a0 = angleOffset + phase + turns * t0;
            float a1 = angleOffset + phase + turns * t1;
            float c0x = Mth.cos(a0);
            float c0z = Mth.sin(a0);
            float c1x = Mth.cos(a1);
            float c1z = Mth.sin(a1);
            vertexShader(out, m, c0x * (middle - half), height * t0, c0z * (middle - half),
                    0.5f, 1.0f - t0, cr, cg, cb);
            vertexShader(out, m, c0x * (middle + half), height * t0, c0z * (middle + half),
                    0.5f, 1.0f - t0, cr, cg, cb);
            vertexShader(out, m, c1x * (middle + half), height * t1, c1z * (middle + half),
                    0.5f, 1.0f - t1, cr, cg, cb);
            vertexShader(out, m, c1x * (middle - half), height * t1, c1z * (middle - half),
                    0.5f, 1.0f - t1, cr, cg, cb);
        }
    }

    /** Rings: a stack of glowing rings that slowly rises along the beam. */
    private static void rings(VertexConsumer out, Matrix4f m, float radius, float height,
                              float r, float g, float b, float alpha, float phase, boolean dynamic) {
        float scroll = dynamic ? phase / Mth.TWO_PI * 0.5f : 0.0f;
        for (int ring = 0; ring < RING_COUNT; ring++) {
            float t = ((float) ring / RING_COUNT + scroll) % 1.0f;
            float ringRadius = radius * (0.62f + 0.26f * Mth.sin(t * Mth.PI * 2.0f + phase));
            float thickness = radius * 0.14f;
            float level = verticalProfile(t) * alpha;
            float y = height * t;
            for (int i = 0; i < RING_SEGMENTS; i++) {
                float a0 = Mth.TWO_PI * i / RING_SEGMENTS;
                float a1 = Mth.TWO_PI * (i + 1) / RING_SEGMENTS;
                float in0x = Mth.cos(a0) * (ringRadius - thickness);
                float in0z = Mth.sin(a0) * (ringRadius - thickness);
                float out0x = Mth.cos(a0) * (ringRadius + thickness);
                float out0z = Mth.sin(a0) * (ringRadius + thickness);
                float in1x = Mth.cos(a1) * (ringRadius - thickness);
                float in1z = Mth.sin(a1) * (ringRadius - thickness);
                float out1x = Mth.cos(a1) * (ringRadius + thickness);
                float out1z = Mth.sin(a1) * (ringRadius + thickness);
                vertex(out, m, in0x, y, in0z, r, g, b, level);
                vertex(out, m, out0x, y, out0z, r, g, b, level);
                vertex(out, m, out1x, y, out1z, r, g, b, level);
                vertex(out, m, in1x, y, in1z, r, g, b, level);
            }
        }
    }

    /** Shader path rings: one flat colour per ring, the height fade comes from the texture. */
    private static void ringsShader(VertexConsumer out, Matrix4f m, float radius, float height,
                                    float r, float g, float b, float alpha, float phase, boolean dynamic) {
        float scroll = dynamic ? phase / Mth.TWO_PI * 0.5f : 0.0f;
        float cr = r * alpha;
        float cg = g * alpha;
        float cb = b * alpha;
        for (int ring = 0; ring < RING_COUNT; ring++) {
            float t = ((float) ring / RING_COUNT + scroll) % 1.0f;
            float ringRadius = radius * (0.62f + 0.26f * Mth.sin(t * Mth.PI * 2.0f + phase));
            float thickness = radius * 0.14f;
            float v = 1.0f - t;
            float y = height * t;
            for (int i = 0; i < RING_SEGMENTS; i++) {
                float a0 = Mth.TWO_PI * i / RING_SEGMENTS;
                float a1 = Mth.TWO_PI * (i + 1) / RING_SEGMENTS;
                vertexShader(out, m, Mth.cos(a0) * (ringRadius - thickness), y, Mth.sin(a0) * (ringRadius - thickness),
                        0.5f, v, cr, cg, cb);
                vertexShader(out, m, Mth.cos(a0) * (ringRadius + thickness), y, Mth.sin(a0) * (ringRadius + thickness),
                        0.5f, v, cr, cg, cb);
                vertexShader(out, m, Mth.cos(a1) * (ringRadius + thickness), y, Mth.sin(a1) * (ringRadius + thickness),
                        0.5f, v, cr, cg, cb);
                vertexShader(out, m, Mth.cos(a1) * (ringRadius - thickness), y, Mth.sin(a1) * (ringRadius - thickness),
                        0.5f, v, cr, cg, cb);
            }
        }
    }

    /** Shader path ground glow: the gradient texture carries the falloff and the ring. */
    private static void glowShader(VertexConsumer out, Matrix4f m, float radius,
                                   float r, float g, float b) {
        for (int i = 0; i < RADIAL_SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / RADIAL_SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / RADIAL_SEGMENTS;
            vertexShader(out, m, 0.0f, 0.022f, 0.0f, 0.5f, 0.5f, r, g, b);
            vertexShader(out, m, Mth.cos(a0) * radius, 0.022f, Mth.sin(a0) * radius,
                    0.5f + 0.5f * Mth.cos(a0), 0.5f + 0.5f * Mth.sin(a0), r, g, b);
            vertexShader(out, m, Mth.cos(a1) * radius, 0.022f, Mth.sin(a1) * radius,
                    0.5f + 0.5f * Mth.cos(a1), 0.5f + 0.5f * Mth.sin(a1), r, g, b);
            vertexShader(out, m, 0.0f, 0.022f, 0.0f, 0.5f, 0.5f, r, g, b);
        }
    }

    /**
     * Vertex of the shader path: a particle format vertex with gradient texture coordinates.
     * The alpha stays at one so the pack never reads the beam as smoke or snow, and the full
     * bright light map keeps it glowing at night and underground.
     */
    private static void vertexShader(VertexConsumer out, Matrix4f m, float x, float y, float z,
                                     float u, float v, float r, float g, float b) {
        out.vertex(m, x, y, z).uv(u, v).color(r, g, b, 1.0f)
                .uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    /** Faces pointing at the camera stay solid, the silhouette gets soft. */
    private static float rimFacing(float nx, float nz, float dirX, float dirZ) {
        float d = nx * dirX + nz * dirZ;
        return 0.42f + 0.58f * d * d;
    }

    /** Slight fade at the very bottom, then a smooth fade out towards the top. */
    private static float verticalProfile(float t) {
        float base = Mth.clamp(t * 6.0f + 0.55f, 0f, 1f);
        return base * (float) Math.pow(1.0f - t, 1.35f);
    }

    /** Slowly rising brightness band inside the column. */
    private static float shimmer(float t, float phase, boolean dynamic) {
        if (!dynamic) return 1f;
        return 0.88f + 0.12f * Mth.sin(t * 5.0f - phase * 2.0f);
    }

    private static void groundDisc(VertexConsumer out, Matrix4f m, float radius,
                                   float r, float g, float b, float a, float phase) {
        int segments = 20;
        for (int i = 0; i < segments; i++) {
            float a0 = phase * 0.08f + Mth.TWO_PI * i / segments;
            float a1 = phase * 0.08f + Mth.TWO_PI * (i + 1) / segments;
            float x0 = Mth.cos(a0) * radius;
            float z0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius;
            float z1 = Mth.sin(a1) * radius;
            vertex(out, m, 0, 0.022f, 0, r, g, b, a);
            vertex(out, m, x0, 0.022f, z0, r, g, b, 0);
            vertex(out, m, x1, 0.022f, z1, r, g, b, 0);
            vertex(out, m, 0, 0.022f, 0, r, g, b, a);
        }
    }

    private static void groundRing(VertexConsumer out, Matrix4f m, float radius,
                                   float r, float g, float b, float a, float phase) {
        int segments = 24;
        float inner = radius * 0.80f;
        for (int i = 0; i < segments; i++) {
            float a0 = phase * -0.12f + Mth.TWO_PI * i / segments;
            float a1 = phase * -0.12f + Mth.TWO_PI * (i + 1) / segments;
            vertex(out, m, Mth.cos(a0) * inner, 0.028f, Mth.sin(a0) * inner, r, g, b, 0);
            vertex(out, m, Mth.cos(a0) * radius, 0.028f, Mth.sin(a0) * radius, r, g, b, a);
            vertex(out, m, Mth.cos(a1) * radius, 0.028f, Mth.sin(a1) * radius, r, g, b, a);
            vertex(out, m, Mth.cos(a1) * inner, 0.028f, Mth.sin(a1) * inner, r, g, b, 0);
        }
    }

    /**
     * Vertex of the beam. Alpha is the weight of that corner: softness towards the silhouette,
     * the fade out towards the top, the fade out at point blank range and the fade of the
     * ground glow. Additive blending turns it into "how much light this corner adds", which is
     * why both render paths can share this and only differ in the shader they run.
     */
    private static void vertex(VertexConsumer out, Matrix4f m, float x, float y, float z,
                               float r, float g, float b, float weight) {
        out.vertex(m, x, y, z).color(r, g, b, weight).endVertex();
    }

    private static void drawName(PoseStack pose, MultiBufferSource buffers, Quaternionf orientation,
                                 Font font, Settings settings, Slot slot) {
        pose.pushPose();
        pose.translate(slot.x, slot.y + settings.nameYOffset, slot.z);
        pose.mulPose(orientation);
        float scale = settings.nameScale;
        pose.scale(-0.025f * scale, -0.025f * scale, 0.025f * scale);
        float x = -slot.nameWidth / 2.0f;
        int textAlpha = (int) (settings.nameAlpha * 255) << 24;
        int background = settings.nameBorder ? ((int) (settings.nameBackground * 255) << 24) : 0;
        font.drawInBatch(slot.name, x, 0, textAlpha | slot.color, false, pose.last().pose(), buffers,
                Font.DisplayMode.SEE_THROUGH, background, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private LootBeamRenderer() {}
}
