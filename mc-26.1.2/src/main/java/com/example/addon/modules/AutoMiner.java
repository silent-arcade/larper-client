package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.util.Automation;
import com.example.addon.util.BaritoneBridge;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AutoMiner extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgInventory = settings.createGroup("Inventory");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks").description("Blocks to mine. Tip: look at one and press \"Add the block I look at\" above.")
        .defaultValue(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE).build());

    private final Setting<Integer> amount = sgGeneral.add(new IntSetting.Builder()
        .name("amount").description("Stop after mining this many (0 = keep going until you stop it).")
        .defaultValue(0).range(0, 10000).sliderRange(0, 256).build());

    private final Setting<Boolean> humanWalk = sgGeneral.add(new BoolSetting.Builder()
        .name("human-walking").description("Baritone looks where it walks, turns smoothly with a little randomness and never parkours.").defaultValue(true).build());

    private final Setting<Boolean> showStatus = sgGeneral.add(new BoolSetting.Builder()
        .name("status").description("Show what it's doing, how many it mined and for how long at the top of the screen.").defaultValue(true).build());

    private final Setting<Boolean> pauseOnMove = sgSafety.add(new BoolSetting.Builder()
        .name("pause-when-you-move").description("Press a movement key to take over: it pauses, and carries on 2 seconds after you let go.").defaultValue(true).build());

    private final Setting<Boolean> pauseOnPlayer = sgSafety.add(new BoolSetting.Builder()
        .name("pause-near-players").description("Pause while another player (not a friend) is close.").defaultValue(true).build());

    private final Setting<Integer> playerRange = sgSafety.add(new IntSetting.Builder()
        .name("player-range").description("How close counts as near (blocks).").defaultValue(24).range(4, 128).sliderRange(8, 64)
        .visible(pauseOnPlayer::get).build());

    private final Setting<Boolean> stopWhenHit = sgSafety.add(new BoolSetting.Builder()
        .name("stop-when-hit").description("If another player hits you, stop mining (the module turns off).").defaultValue(true).build());

    private final Setting<Boolean> lookAtAttacker = sgSafety.add(new BoolSetting.Builder()
        .name("look-at-attacker").description("When a player hits you, turn to face them.").defaultValue(true)
        .visible(stopWhenHit::get).build());

    private final Setting<Boolean> pauseOnHealth = sgSafety.add(new BoolSetting.Builder()
        .name("pause-low-health").description("Pause while your health is low (eat or heal, it resumes after).").defaultValue(true).build());

    private final Setting<Double> minHealth = sgSafety.add(new DoubleSetting.Builder()
        .name("min-health").description("Pause below this many hearts.").defaultValue(6).range(1, 10).sliderRange(1, 10)
        .visible(pauseOnHealth::get).build());

    private final Setting<Boolean> dropJunk = sgInventory.add(new BoolSetting.Builder()
        .name("drop-junk").description("Drop junk items when your inventory is nearly full.").defaultValue(true).build());

    private final Setting<List<Item>> junk = sgInventory.add(new ItemListSetting.Builder()
        .name("junk").description("Items that count as junk.")
        .defaultValue(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.NETHERRACK, Items.DIRT, Items.GRAVEL, Items.TUFF, Items.ANDESITE, Items.DIORITE, Items.GRANITE)
        .visible(dropJunk::get).build());

    private final Setting<Integer> keepFree = sgInventory.add(new IntSetting.Builder()
        .name("keep-free-slots").description("Drop junk once fewer than this many slots are free.").defaultValue(2).range(1, 20).sliderRange(1, 9)
        .visible(dropJunk::get).build());

    private final Setting<Boolean> stopWhenFull = sgInventory.add(new BoolSetting.Builder()
        .name("stop-when-full").description("Stop mining when your inventory is full (and there's no junk left to drop).").defaultValue(true).build());

    private boolean running;
    private String pauseReason;
    private int idleTicks, dropCooldown, mined;
    private long startedAt;
    private Map<String, Object> savedBaritone;

    public AutoMiner() {
        super(AddonTemplate.AUTOMATION, "auto-miner", "Mines your chosen ores with Baritone, pausing for players, low health or when you move.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WHorizontalList row = theme.horizontalList();
        row.add(theme.button("Add the block I look at")).widget().action = () -> {
            if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
                ChatUtils.warning("Auto Miner: look at a block first.");
                return;
            }
            Block b = mc.level.getBlockState(hit.getBlockPos()).getBlock();
            if (blocks.get().contains(b)) {
                ChatUtils.info("Auto Miner: %s is already in the list.", b.getName().getString());
                return;
            }
            List<Block> list = new ArrayList<>(blocks.get());
            list.add(b);
            blocks.set(list);
            ChatUtils.info("Auto Miner: added %s.", b.getName().getString());
        };
        row.add(theme.button("Start / Stop")).widget().action = this::toggle;
        return row;
    }

    @Override
    public void onActivate() {
        running = false;
        if (!BaritoneBridge.available()) {
            ChatUtils.error("Auto Miner: Baritone didn't load, so it can't mine. Reinstall Larper Client and restart.");
            toggle();
            return;
        }
        if (blocks.get().isEmpty()) {
            ChatUtils.error("Auto Miner: pick at least one block to mine (look at one and press \"Add the block I look at\").");
            toggle();
            return;
        }
        running = true;
        pauseReason = null;
        idleTicks = 0;
        mined = 0;
        startedAt = System.currentTimeMillis();
        savedBaritone = humanWalk.get() ? BaritoneBridge.apply(BaritoneBridge.humanWalking()) : null;
        String ids = blocks.get().stream().map(b -> BuiltInRegistries.BLOCK.getKey(b).toString()).collect(Collectors.joining(" "));
        BaritoneBridge.command("mine " + (amount.get() > 0 ? amount.get() + " " : "") + ids);
        ChatUtils.info("Auto Miner started: %s. Move to take over, toggle the module to stop.", names());
    }

    @Override
    public void onDeactivate() {
        if (!running) return;
        running = false;
        BaritoneBridge.command("stop");
        BaritoneBridge.restore(savedBaritone);
        savedBaritone = null;
    }

    private void finish(String why) {
        ChatUtils.info("Auto Miner: %s Mined %d in %s.", why, mined, Automation.time(System.currentTimeMillis() - startedAt));
        toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!running || mc.player == null || mc.level == null) return;

        String reason = Automation.pauseReason(pauseOnMove.get(), pauseOnPlayer.get(), playerRange.get(), pauseOnHealth.get(), minHealth.get());
        if (reason != null && pauseReason == null) {
            BaritoneBridge.command("pause");
            if (!reason.startsWith("you're")) ChatUtils.warning("Auto Miner paused: %s", reason);
        } else if (reason == null && pauseReason != null) {
            BaritoneBridge.command("resume");
            if (!pauseReason.startsWith("you're")) ChatUtils.info("Auto Miner resumed.");
        }
        pauseReason = reason;

        if (dropJunk.get() && mc.screen == null && --dropCooldown <= 0 && freeSlots() < keepFree.get()) {
            for (int i = 0; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getItem(i);
                if (!s.isEmpty() && junk.get().contains(s.getItem())) {
                    InvUtils.drop().slot(i);
                    dropCooldown = 4;
                    break;
                }
            }
        }

        if (stopWhenFull.get() && freeSlots() == 0 && !hasJunk()) {
            finish("stopped, your inventory is full.");
            return;
        }

        if (pauseReason == null && !BaritoneBridge.mining()) {
            if (++idleTicks > 60) finish(amount.get() > 0 && mined >= amount.get() ? "done!" : "finished, nothing more to mine nearby.");
        } else idleTicks = 0;
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!running || mc.player == null || !event.newState.isAir() || !blocks.get().contains(event.oldState.getBlock())) return;
        if (mc.player.getEyePosition().distanceToSqr(event.pos.getCenter()) < 49) mined++;
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!stopWhenHit.get() || !(event.packet instanceof ClientboundDamageEventPacket p)) return;
        mc.execute(() -> {
            if (!running || mc.player == null || mc.level == null || p.entityId() != mc.player.getId()) return;
            if (!(mc.level.getEntity(p.sourceCauseId()) instanceof Player attacker) || attacker == mc.player) return;
            if (lookAtAttacker.get()) {
                mc.player.setYRot((float) Rotations.getYaw(attacker));
                mc.player.setXRot((float) Rotations.getPitch(attacker));
            }
            finish("stopped, " + attacker.getName().getString() + " hit you.");
        });
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!running || !showStatus.get() || mc.options.hideGui) return;
        String count = amount.get() > 0 ? mined + " / " + amount.get() : String.valueOf(mined);
        List<String> lines = new ArrayList<>();
        lines.add("Auto Miner: mined " + count + "  (" + (amount.get() > 0 ? Automation.timing(startedAt, mined, amount.get()) : Automation.time(System.currentTimeMillis() - startedAt)) + ")");
        lines.add(pauseReason != null ? "  PAUSED: " + pauseReason : "  looking for " + names() + "  -  " + freeSlots() + " free slots");
        Automation.status(event.graphics, "Auto Miner", lines, 0xFF8FD8FF);
    }

    private String names() {
        List<Block> list = blocks.get();
        String s = list.stream().limit(3).map(b -> b.getName().getString()).collect(Collectors.joining(", "));
        return list.size() > 3 ? s + " +" + (list.size() - 3) + " more" : s;
    }

    private int freeSlots() {
        int free = 0;
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).isEmpty()) free++;
        return free;
    }

    private boolean hasJunk() {
        if (!dropJunk.get()) return false;
        for (int i = 0; i < 36; i++) if (junk.get().contains(mc.player.getInventory().getItem(i).getItem())) return true;
        return false;
    }
}
