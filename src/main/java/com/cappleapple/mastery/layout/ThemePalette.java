package com.cappleapple.mastery.layout;

import com.cappleapple.mastery.data.TreeTheme;

/** Shared color calculation for node rings, connector bands, and the editor preview. */
public final class ThemePalette {
    public enum State { LOCKED, ENABLED, DISABLED }
    private ThemePalette() {}
    public static int color(TreeTheme theme, double inward, State state, double alpha) {
        inward = Math.clamp(inward, 0, 1);
        double mix = theme.gradient() == 0 ? (inward < .5 ? 0 : 1)
                : Math.clamp((inward - (1 - theme.gradient()) / 2) / theme.gradient(), 0, 1);
        int outer = state == State.LOCKED ? 0x505050 : theme.outerRgb();
        int inner = state == State.LOCKED ? 0xA0A0A0 : theme.innerRgb();
        double brightness = state == State.DISABLED ? .4 : 1;
        int rgb = 0;
        for (int shift : new int[]{16, 8, 0}) {
            int a = (outer >> shift) & 255, b = (inner >> shift) & 255;
            rgb |= (int)Math.round((a + (b - a) * mix) * brightness) << shift;
        }
        return (int)Math.round(255 * Math.clamp(alpha, 0, 1)) << 24 | rgb;
    }
}
