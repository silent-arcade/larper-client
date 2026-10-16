package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.mixin.MinecraftClientAccessor;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import java.util.List;
import java.util.Random;

public class FastPlace extends Module {
    public enum ItemMode { Blocks, All, Whitelist, Blacklist }

    private static final int VANILLA_COOLDOWN = 4;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgItems = settings.createGroup("Items");
    private final SettingGroup sgConditions = settings.createGroup("Conditions");

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay").description("Ticks between placements. 0 = every tick (vanilla is 4).")
        .defaultValue(0).range(0, 3).sliderRange(0, 3).build());

    private final Setting<Boolean> randomize = sgGeneral.add(new BoolSetting.Builder()
        .name("random-delay").description("Pick a random delay between 'delay' and 'max-delay' for each placement.")
        .defaultValue(false).build());

    private final Setting<Integer> maxDelay = sgGeneral.add(new IntSetting.Builder()
        .name("max-delay").description("Longest random delay (ticks).")
        .defaultValue(2).range(0, 3).sliderRange(0, 3).visible(randomize::get).build());

    private final Setting<Integer> startDelay = sgGeneral.add(new IntSetting.Builder()
        .name("start-delay").description("Hold right-click this many ticks before it speeds up (taps stay normal).")
        .defaultValue(0).range(0, 20).sliderRange(0, 10).build());

    private final Setting<ItemMode> items = sgItems.add(new EnumSetting.Builder<ItemMode>()
        .name("items").description("Blocks: only blocks. All: anything you right-click. Whitelist/Blacklist: use the list below.")
        .defaultValue(ItemMode.Blocks).build());

    private final Setting<List<Item>> list = sgItems.add(new ItemListSetting.Builder()
        .name("item-list").description("Items for Whitelist / Blacklist.")
        .defaultValue(Items.END_CRYSTAL, Items.EXPERIENCE_BOTTLE)
        .visible(() -> items.get() == ItemMode.Whitelist || items.get() == ItemMode.Blacklist).build());

    private final Setting<Boolean> offhand = sgItems.add(new BoolSetting.Builder()
        .name("offhand").description("Also count the item in your offhand (when your main hand doesn't qualify).")
        .defaultValue(true).build());

    private final Setting<Boolean> onlyOnBlock = sgConditions.add(new BoolSetting.Builder()
        .name("only-on-block").description("Only when your crosshair is on a block.")
        .defaultValue(false).build());

    private final Setting<Boolean> notOnEntity = sgConditions.add(new BoolSetting.Builder()
        .name("not-on-entity").description("Normal speed while looking at a mob or player.")
        .defaultValue(true).build());

    private final Setting<Boolean> onlyOnGround = sgConditions.add(new BoolSetting.Builder()
        .name("only-on-ground").description("Only while standing on the ground.")
        .defaultValue(false).build());

    private final Setting<Boolean> onlySneaking = sgConditions.add(new BoolSetting.Builder()
        .name("only-sneaking").description("Only while sneaking (e.g. for bridging).")
        .defaultValue(false).build());

    private final Setting<Boolean> notWhileUsing = sgConditions.add(new BoolSetting.Builder()
        .name("not-while-eating").description("Normal speed while eating, drinking or drawing a bow.")
        .defaultValue(true).build());

    private final Random rng = new Random();
    private int held, current;

    public FastPlace() {
        super(AddonTemplate.AUTOMATION, "fast-place", "Places blocks (or uses items) faster while you hold right-click.");
    }

    @Override
    public void onActivate() {
        held = 0;
        current = delay.get();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.screen != null) return;
        held = mc.options.keyUse.isDown() ? held + 1 : 0;
        if (held == 0 || held <= startDelay.get() || !allowed()) return;

        MinecraftClientAccessor client = (MinecraftClientAccessor) mc;
        int cooldown = client.larper$getItemUseCooldown();
        if (cooldown == VANILLA_COOLDOWN) current = randomize.get() ? delay.get() + rng.nextInt(Math.max(0, maxDelay.get() - delay.get()) + 1) : delay.get();
        if (cooldown > current + 1) client.larper$setItemUseCooldown(current + 1);
    }

    private boolean allowed() {
        if (onlyOnGround.get() && !mc.player.onGround()) return false;
        if (onlySneaking.get() && !mc.player.isShiftKeyDown()) return false;
        if (notWhileUsing.get() && mc.player.isUsingItem()) return false;
        HitResult hit = mc.hitResult;
        if (onlyOnBlock.get() && (hit == null || hit.getType() != HitResult.Type.BLOCK)) return false;
        if (notOnEntity.get() && hit != null && hit.getType() == HitResult.Type.ENTITY) return false;
        return qualifies(mc.player.getMainHandItem()) || (offhand.get() && qualifies(mc.player.getOffhandItem()));
    }

    private boolean qualifies(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (items.get()) {
            case Blocks -> stack.getItem() instanceof BlockItem;
            case All -> true;
            case Whitelist -> list.get().contains(stack.getItem());
            case Blacklist -> !list.get().contains(stack.getItem());
        };
    }
}
