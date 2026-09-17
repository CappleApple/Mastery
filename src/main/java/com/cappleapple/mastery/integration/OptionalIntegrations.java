package com.cappleapple.mastery.integration;

/** Required Iron/Curios/Better Combat integrations; no spell implementation is registered here. */
public final class OptionalIntegrations {
    private OptionalIntegrations() {}
    public static void initialize() { CuriosIntegration.register(); BetterCombatIntegration.register(); }
}
