package com.example.addon;

import com.example.addon.commands.CommandExample;
import com.example.addon.modules.BPS;
import com.example.addon.modules.CinematicDeath;
import com.example.addon.modules.CustomTotem;
import com.example.addon.modules.OreSim;
import com.example.addon.modules.TntCartAssist;
import com.example.addon.modules.TurnOffAll;
import com.example.addon.modules.OrbitOrbs;
import com.example.addon.modules.Trail;
import com.example.addon.modules.ChinaHat;
import com.example.addon.modules.JumpRings;
import com.example.addon.modules.Wings;
import com.example.addon.modules.AutoFirework;
import com.example.addon.commands.SeedCommand;
import com.example.addon.commands.RegionCommand;
import com.example.addon.modules.RegionRoute;
import com.example.addon.modules.Compass;
import com.example.addon.modules.CustomSky;
import com.example.addon.modules.HandAura;
import com.example.addon.modules.HitSounds;
import com.example.addon.modules.KeyWindCharge;
import com.example.addon.modules.Knife;
import com.example.addon.modules.Twerk;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Larper Client");
    public static final HudGroup HUD_GROUP = new HudGroup("Larper Client");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Larper Client");

        // Modules
        Modules.get().add(new Compass());
        Modules.get().add(new Knife());
        Modules.get().add(new HitSounds());
        Modules.get().add(new HandAura());
        Modules.get().add(new CustomSky());
        Modules.get().add(new KeyWindCharge());
        Modules.get().add(new Twerk());
        Modules.get().add(new BPS());
        Modules.get().add(new CinematicDeath());
        Modules.get().add(new CustomTotem());
        Modules.get().add(new OreSim());
        Modules.get().add(new TntCartAssist());
        Modules.get().add(new TurnOffAll());
        Modules.get().add(new OrbitOrbs());
        Modules.get().add(new Trail());
        Modules.get().add(new ChinaHat());
        Modules.get().add(new JumpRings());
        Modules.get().add(new Wings());
        Modules.get().add(new AutoFirework());
        Modules.get().add(new RegionRoute());
        Modules.get().add(new com.example.addon.modules.FastPlace());
        Modules.get().add(new com.example.addon.modules.SkinChanger());

        // Commands
        Commands.add(new CommandExample());
        Commands.add(new SeedCommand());
        Commands.add(new RegionCommand());

    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
