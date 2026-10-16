package com.example.addon.commands;

import com.example.addon.modules.RegionRoute;
import com.example.addon.regions.DonutRegions;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

/**
 * DonutSMP region routes:
 *   .region            which region you're in
 *   .region <to>       from where you are to that region (also points the Region Route module at it)
 *   .region <from> <to> from one region's centre to another's
 */
public class RegionCommand extends Command {
    public RegionCommand() {
        super("region", "DonutSMP: route to another region, e.g. .region 5 or .region 28 5");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(ctx -> {
            int here = DonutRegions.regionAt(mc.player.getX(), mc.player.getZ());
            if (here < 0) error("You're outside the DonutSMP region map.");
            else info("You're in region %d (%s).", here, DonutRegions.continent(here));
            return SINGLE_SUCCESS;
        });

        builder.then(argument("to", IntegerArgumentType.integer(1)).executes(ctx -> {
            int to = IntegerArgumentType.getInteger(ctx, "to");
            route(mc.player.getX(), mc.player.getZ(), "you", to, true);
            return SINGLE_SUCCESS;
        }).then(argument("to2", IntegerArgumentType.integer(1)).executes(ctx -> {
            int from = IntegerArgumentType.getInteger(ctx, "to"), to = IntegerArgumentType.getInteger(ctx, "to2");
            double[] f = DonutRegions.center(from);
            if (f == null) {
                error("Region %d isn't on the DonutSMP map.", from);
                return SINGLE_SUCCESS;
            }
            route(f[0], f[1], "region " + from, to, false);
            return SINGLE_SUCCESS;
        })));
    }

    private void route(double x, double z, String fromName, int to, boolean live) {
        double[] c = DonutRegions.center(to);
        if (c == null) {
            error("Region %d isn't on the DonutSMP map.", to);
            return;
        }
        double dx = c[0] - x, dz = c[1] - z, dist = Math.hypot(dx, dz);
        float yaw = DonutRegions.yawTo(x, z, c[0], c[1]);
        info("Region %d (%s) centre: X %d, Z %d", to, DonutRegions.continent(to), Math.round(c[0]), Math.round(c[1]));
        info("Nether: %s (%,d blocks in the nether)", DonutRegions.nether(c[0], c[1]), Math.round(dist / 8));
        info("From %s: %,d blocks %s (yaw %.0f) - %s.", fromName, Math.round(dist), DonutRegions.compass(yaw), yaw,
            DonutRegions.axes(dx, dz));
        if (live) {
            info("Facing: %s.", DonutRegions.turn(mc.player.getYRot(), yaw));
            RegionRoute rr = Modules.get().get(RegionRoute.class);
            rr.setTarget(to);
            if (!rr.isActive()) rr.toggle();
        }
    }
}
