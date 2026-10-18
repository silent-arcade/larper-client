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
    public static final Category CATEGORY = new Category("Larper Client", () -> net.minecraft.world.item.Items.END_CRYSTAL.getDefaultInstance());
    public static final Category AUTOMATION = new Category("Larper Automation", () -> net.minecraft.world.item.Items.DIAMOND_PICKAXE.getDefaultInstance());
    public static final Category VISUAL = new Category("Larper Visual", () -> net.minecraft.world.item.Items.FIREWORK_ROCKET.getDefaultInstance());
    public static final HudGroup HUD_GROUP = new HudGroup("Larper Client");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Larper Client");

        Modules.get().add(new Compass());
        Modules.get().add(new Knife());
        Modules.get().add(new HitSounds());
        Modules.get().add(new HandAura());
        Modules.get().add(new CustomSky());
        Modules.get().add(new KeyWindCharge());
        Modules.get().add(new com.example.addon.modules.PearlCatch());
        Modules.get().add(new com.example.addon.modules.XpMacro());
        Modules.get().add(new Twerk());
        Modules.get().add(new BPS());
        Modules.get().add(new CinematicDeath());
        Modules.get().add(new CustomTotem());
        Modules.get().add(new OreSim());
        Modules.get().add(new com.example.addon.modules.DebrisFinder());
        Modules.get().add(new TntCartAssist());
        Modules.get().add(new TurnOffAll());
        Modules.get().add(new com.example.addon.modules.SilentCrystal());
        Modules.get().add(new com.example.addon.modules.SilentAnchor());
        Modules.get().add(new com.example.addon.modules.RenderMethod());
        Modules.get().add(new com.example.addon.modules.AutoHomeMethod());
        Modules.get().add(new OrbitOrbs());
        Modules.get().add(new Trail());
        Modules.get().add(new ChinaHat());
        Modules.get().add(new JumpRings());
        Modules.get().add(new Wings());
        Modules.get().add(new AutoFirework());
        Modules.get().add(new RegionRoute());
        Modules.get().add(new com.example.addon.modules.FastPlace());
        Modules.get().add(new com.example.addon.modules.SkinChanger());
        Modules.get().add(new com.example.addon.modules.DamageNumbers());
        Modules.get().add(new com.example.addon.modules.TargetCircle());
        Modules.get().add(new com.example.addon.modules.KillEffects());
        Modules.get().add(new com.example.addon.modules.Atmosphere());
        Modules.get().add(new com.example.addon.modules.CustomCrosshair());
        Modules.get().add(new com.example.addon.modules.Footprints());
        Modules.get().add(new com.example.addon.modules.MagicCircle());
        Modules.get().add(new com.example.addon.modules.PlayerModel());
        Modules.get().add(new com.example.addon.modules.AutoMiner());
        Modules.get().add(new com.example.addon.modules.AreaMiner());
        Modules.get().add(new com.example.addon.modules.SchematicBuilder());
        Modules.get().add(new com.example.addon.modules.ScreenShaders());
        Modules.get().add(new com.example.addon.modules.AnimeFX());
        Modules.get().add(new com.example.addon.modules.EnergyShield());
        Modules.get().add(new com.example.addon.modules.ElectricAura());
        Modules.get().add(new com.example.addon.modules.ShootingStars());
        Modules.get().add(new com.example.addon.modules.SmoothCamera());
        Modules.get().add(new com.example.addon.modules.SmoothThirdPerson());
        Modules.get().add(new com.example.addon.modules.DynamicFov());
        Modules.get().add(new com.example.addon.modules.CameraTilt());
        Modules.get().add(new com.example.addon.modules.CrystalFX());
        Modules.get().add(new com.example.addon.modules.AnchorFX());
        Modules.get().add(new com.example.addon.modules.SpearFX());
        Modules.get().add(new com.example.addon.modules.Keybinds());
        Modules.get().add(new com.example.addon.modules.ModuleList());
        Modules.get().add(new com.example.addon.modules.MaceFX());
        Modules.get().add(new com.example.addon.modules.NoFog());
        Modules.get().add(new com.example.addon.modules.ClickGuiStyle());
        Modules.get().add(new com.example.addon.modules.Triggerbot());
        Modules.get().add(new com.example.addon.modules.ShieldBreaker());
        Modules.get().add(new com.example.addon.modules.AutoMace());
        Modules.get().add(new com.example.addon.modules.SpinBot());
        Modules.get().add(new com.example.addon.modules.ElytraJump());
        Modules.get().add(new com.example.addon.modules.SnapTap());
        Modules.get().add(new com.example.addon.modules.KeyPearl());
        Modules.get().add(new com.example.addon.modules.CrystalOptimizer());
        Modules.get().add(new com.example.addon.modules.TntTimer());
        Modules.get().add(new com.example.addon.modules.HitParticles());
        Modules.get().add(new com.example.addon.modules.AutoF5());
        Modules.get().add(new com.example.addon.modules.SkeletonESP());
        Modules.get().add(new com.example.addon.modules.Aspect());
        Modules.get().add(new com.example.addon.modules.Drugs());

        Commands.add(new CommandExample());
        Commands.add(new SeedCommand());
        Commands.add(new RegionCommand());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
        Modules.registerCategory(AUTOMATION);
        Modules.registerCategory(VISUAL);
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
