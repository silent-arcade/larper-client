package com.example.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class SilentAnchor extends SilentBase {
    private final Setting<Integer> placeDelay = sg.add(new IntSetting.Builder()
        .name("place-delay").description("Ticks after placing the anchor (20 = 1 second).").defaultValue(2).range(0, 20).sliderRange(0, 10).build());

    private final Setting<Integer> chargeDelay = sg.add(new IntSetting.Builder()
        .name("charge-delay").description("Ticks after charging it with glowstone.").defaultValue(1).range(0, 20).sliderRange(0, 10).build());

    private final Setting<Integer> explodeDelay = sg.add(new IntSetting.Builder()
        .name("explode-delay").description("Ticks after blowing it up.").defaultValue(2).range(0, 20).sliderRange(0, 10).build());

    private int timer;
    private boolean warned;

    public SilentAnchor() {
        super("silent-anchor", "Anchor macro: hold the key on a block to place, charge and blow a respawn anchor with silent swaps. Safe, Blatant or Fast mode.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        warned = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer > 0 && --timer > 0) return;
        if (!held()) {
            releaseSlot();
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == BlockHitResult.Type.MISS) return;
        if (mc.level.dimension() == Level.NETHER) {
            if (!warned) warning("Anchors don't explode in the Nether.");
            warned = true;
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        FindItemResult glow = InvUtils.findInHotbar(Items.GLOWSTONE);

        if (!state.is(Blocks.RESPAWN_ANCHOR)) {
            FindItemResult anchor = InvUtils.findInHotbar(Items.RESPAWN_ANCHOR);
            if (!anchor.found() || !glow.found()) return;
            if (!use(anchor.slot(), hit)) return;
            timer = delay(placeDelay.get());
            if (fast()) {
                BlockPos placed = state.canBeReplaced() ? pos : pos.relative(hit.getDirection());
                BlockHitResult on = new BlockHitResult(Vec3.atCenterOf(placed), Direction.UP, placed, false);
                use(glow.slot(), on);
                explode(placed, on);
            }
        } else if (state.getValue(RespawnAnchorBlock.CHARGE) == 0) {
            if (!glow.found()) return;
            if (!use(glow.slot(), hit)) return;
            timer = delay(chargeDelay.get());
        } else if (explode(pos, hit)) timer = delay(explodeDelay.get());
    }

    private boolean explode(BlockPos pos, BlockHitResult hit) {
        if (!selfOk(DamageUtils.anchorDamage(mc.player, Vec3.atCenterOf(pos)))) return false;
        FindItemResult with = InvUtils.findInHotbar(Items.TOTEM_OF_UNDYING);
        if (!with.found()) with = InvUtils.findInHotbar(st -> !st.is(Items.GLOWSTONE));
        if (!with.found()) return false;
        return use(with.slot(), hit);
    }
}
