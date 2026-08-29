package dev.yuzhe.aeaffinity.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AeAffinityConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<ActivationMode> ACTIVATION = BUILDER
            .comment("OFF disables scheduling; ANCHORED enables grids with a command anchor; ALL enables every grid.")
            .defineEnum("activation", ActivationMode.ANCHORED);

    public static final ModConfigSpec.BooleanValue CHARGE_ENERGY = BUILDER
            .comment("Charge the network's normal AE2 insertion energy for background migrations.")
            .define("chargeEnergy", true);

    public static final ModConfigSpec.IntValue MIN_IDLE_TICKS = BUILDER
            .comment("Fastest retry interval after useful work. 200 ticks = 10 seconds at 20 TPS.")
            .defineInRange("minIdleTicks", 200, 20, 72_000);

    public static final ModConfigSpec.IntValue MAX_IDLE_TICKS = BUILDER
            .comment("Slowest retry interval after stable rounds. 18000 ticks = 15 minutes at 20 TPS.")
            .defineInRange("maxIdleTicks", 18_000, 20, 72_000);

    public static final ModConfigSpec.IntValue PLANNING_TICKS = BUILDER
            .comment("Read-only roulette planning ticks per round.")
            .defineInRange("planningTicks", 4, 1, 100);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private AeAffinityConfig() {
    }
}
