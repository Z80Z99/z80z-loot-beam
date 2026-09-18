package com.github.z80z.lootbeam.config;

/**
 * Shapes the loot beam can be drawn in.
 *
 * <p>Every style works with and without a shader pack, and every style keeps the close range
 * fade, the fade towards the top and the rarity colour.</p>
 */
public enum BeamStyle {
    /** Three nested shells with a bright core, the original look. */
    CLASSIC,
    /** A thin, intense core, like a laser rod. */
    LASER,
    /** A wide, soft column of light. */
    SOFT,
    /** Narrows towards the top into a spire. */
    CONE,
    /** A single ribbon that winds around the beam axis while it rises. */
    SPIRAL,
    /** Two ribbons winding around the axis opposite each other. */
    DOUBLE_HELIX,
    /** A stack of glowing rings that slowly rises along the beam. */
    RINGS
}
