package com.example.addon.commands;

import com.example.addon.modules.OreSim;
import com.example.addon.oresim.Seeds;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

/** .seed (show), .seed <seed> (save for this server), .seed delete. Used by OreSim. */
public class SeedCommand extends Command {
    public SeedCommand() {
        super("seed", "Get or set the seed of the current world (for OreSim).");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(ctx -> {
            Long seed = Seeds.get();
            if (seed == null) error("No seed saved for this world. Set one with .seed <seed>");
            else info("Seed: %d", seed);
            return SINGLE_SUCCESS;
        });

        builder.then(literal("delete").executes(ctx -> {
            info(Seeds.delete() ? "Deleted the seed for this world." : "No seed was saved for this world.");
            Modules.get().get(OreSim.class).reload();
            return SINGLE_SUCCESS;
        }));

        builder.then(argument("seed", StringArgumentType.greedyString()).executes(ctx -> {
            Seeds.set(StringArgumentType.getString(ctx, "seed"));
            info("Seed saved: %d", Seeds.get());
            Modules.get().get(OreSim.class).reload();
            return SINGLE_SUCCESS;
        }));
    }
}
