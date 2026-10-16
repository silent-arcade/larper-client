package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.knife.Anim;
import com.example.addon.knife.CsgoViewmodel;
import com.example.addon.knife.Finish;
import com.example.addon.knife.KnifeType;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

public class Knife extends Module {
    private static final int SLOTS = 36;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<KnifeType> type = sgGeneral.add(new EnumSetting.Builder<KnifeType>()
        .name("knife").description("Which knife to hold.").defaultValue(KnifeType.Butterfly)
        .onChanged(t -> startDeploy())
        .build());

    private final Setting<Finish> finish = sgGeneral.add(new EnumSetting.Builder<Finish>()
        .name("finish").description("Blade skin.").defaultValue(Finish.Fade)
        .build());

    private final Setting<Keybind> inspectBind = sgGeneral.add(new KeybindSetting.Builder()
        .name("inspect").description("Key that plays the inspect animation.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_Y))
        .action(this::inspect)
        .build());

    private final Setting<Double> inspectSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("inspect-speed").description("How fast the inspect plays. 1 = normal, lower = slow motion, higher = faster.")
        .defaultValue(1).range(0.25, 3).sliderRange(0.25, 3)
        .build());

    private final Setting<Double> size = sgGeneral.add(new DoubleSetting.Builder()
        .name("size").description("Size of the CS:GO butterfly knife and arms on screen.")
        .defaultValue(1).range(0.3, 2).sliderRange(0.3, 2)
        .visible(this::hasViewmodel)
        .build());

    private final Setting<Boolean> leftArm = sgGeneral.add(new BoolSetting.Builder()
        .name("left-arm").description("Shows the left arm in the CS:GO butterfly view. Off = only your right arm and the knife.")
        .defaultValue(true)
        .visible(this::hasViewmodel)
        .build());

    private final Setting<Boolean> thirdPerson = sgGeneral.add(new BoolSetting.Builder()
        .name("third-person").description("Also shows the knife in your hand in third person (F5). Only you see it.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> emptySlot = sgGeneral.add(new BoolSetting.Builder()
        .name("empty-slot").description("Puts the knife in an empty inventory slot you can select.")
        .defaultValue(true)
        .build());

    private final Setting<java.util.List<net.minecraft.item.Item>> replaceItems = sgGeneral.add(new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
        .name("replace-items").description("Items that look like your knife instead, e.g. your sword: in your hand, in third person and in your inventory. Only on your screen; the real item still works as normal.")
        .build());

    private final Setting<Integer> slotSetting = sgGeneral.add(new IntSetting.Builder()
        .name("slot").description("Inventory slot the knife sits in.").defaultValue(-1).range(-1, SLOTS - 1)
        .visible(() -> false)
        .build());

    private boolean onCursor, dragPending;
    private long pickedAt;

    private Anim playing;
    private long playStart;
    private boolean wasHolding;
    private final CsgoViewmodel butterflyView = new CsgoViewmodel("butterfly"), karambitView = new CsgoViewmodel("karambit");
    private KnifeType shownType;

    private boolean hasViewmodel() {
        return type.get() == KnifeType.Butterfly || type.get() == KnifeType.Karambit;
    }

    private CsgoViewmodel viewmodel() {
        return type.get() == KnifeType.Karambit ? karambitView : butterflyView;
    }
    private boolean csgoWasShown, lastSwinging;
    private int lastSwingTicks;

    private final float[] pose = new float[Anim.CHANNELS], out = new float[Anim.CHANNELS], blendFrom = new float[Anim.CHANNELS];
    private long blendStart;
    private static final float BLEND = 0.22f;

    public Knife() {
        super(AddonTemplate.VISUAL, "knife", "A client-side CS2 knife in an empty slot. Only you can see it.");
    }

    @Override
    public void onActivate() {
        onCursor = false;
        wasHolding = false;
        playing = null;
        resolveSlot();
    }

    @Override
    public void onDeactivate() {
        onCursor = false;
        playing = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>)) onCursor = false;
        resolveSlot();
    }

    public KnifeType type() {
        return type.get();
    }

    public Finish finish() {
        return finish.get();
    }

    public ItemStack stack() {
        return type.get().stack(finish.get());
    }

    public int slot() {
        if (!emptySlot.get()) return -1;
        int s = slotSetting.get();
        return s >= 0 && mc.player != null && mc.player.getInventory().getStack(s).isEmpty() ? s : -1;
    }

    private void resolveSlot() {
        if (mc.player == null || slot() >= 0) return;
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < SLOTS; i++) {
            if (inv.getStack(i).isEmpty()) {
                slotSetting.set(i);
                return;
            }
        }
    }

    public boolean isOnCursor() {
        return onCursor;
    }

    public boolean showsIn(Slot slot) {
        return isActive() && !onCursor && isPlayerSlot(slot) && slot.getIndex() == slot() && !slot.hasStack();
    }

    public boolean showsInHotbar(int hotbarSlot) {
        return isActive() && !onCursor && hotbarSlot == slot();
    }

    public boolean onSlotClick(Slot slot, SlotActionType action) {
        if (!isActive() || mc.player == null) return false;

        if (onCursor) {
            if (System.currentTimeMillis() - pickedAt < 120) return true;
            place(slot);
            return true;
        }

        if (action == SlotActionType.PICKUP && showsIn(slot) && mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            onCursor = true;
            dragPending = true;
            pickedAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public boolean onRelease(Slot slot) {
        if (!onCursor || !dragPending) return false;
        dragPending = false;
        if (slot == null || !isPlayerSlot(slot) || slot.getIndex() == slotSetting.get()) return false;
        place(slot);
        return true;
    }

    private void place(Slot slot) {
        if (slot != null && isPlayerSlot(slot) && !slot.hasStack()) slotSetting.set(slot.getIndex());
        onCursor = false;
        dragPending = false;
    }

    public void cancelDrag() {
        onCursor = false;
        dragPending = false;
    }

    private boolean isPlayerSlot(Slot slot) {
        return slot != null && mc.player != null && slot.inventory == mc.player.getInventory() && slot.getIndex() < SLOTS;
    }

    public boolean shouldHold() {
        if (!isActive() || onCursor || mc.player == null) return false;
        if (replaces(mc.player.getMainHandStack())) return true;
        int s = slot();
        return s >= 0 && s < 9 && s == mc.player.getInventory().getSelectedSlot();
    }

    public boolean replaces(ItemStack stack) {
        return isActive() && !stack.isEmpty() && replaceItems.get().contains(stack.getItem());
    }

    public boolean replacesIcon(ItemStack stack) {
        if (mc.player == null || !replaces(stack)) return false;
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) if (inv.getStack(i) == stack) return true;
        return false;
    }

    public boolean showsInThirdPerson(net.minecraft.entity.LivingEntity entity) {
        return thirdPerson.get() && entity == mc.player && shouldHold();
    }

    private void inspect() {
        if (mc.currentScreen != null || !shouldHold()) return;
        if (hasViewmodel()) {
            viewmodel().inspect();
            return;
        }
        blend();
        play(type.get().inspect);
    }

    public boolean showsViewmodel() {
        return hasViewmodel() && shouldHold() && mc.options.getPerspective().isFirstPerson();
    }

    public void renderViewmodel(net.minecraft.client.gui.DrawContext ctx) {
        boolean on = showsViewmodel();
        if (on && (!csgoWasShown || shownType != type.get())) viewmodel().takeOut();
        csgoWasShown = on;
        shownType = type.get();
        if (!on) return;
        var p = mc.player;
        if (p.handSwinging && (!lastSwinging || p.handSwingTicks < lastSwingTicks)) viewmodel().attack();
        lastSwinging = p.handSwinging;
        lastSwingTicks = p.handSwingTicks;
        viewmodel().render(ctx, size.get().floatValue(), inspectSpeed.get().floatValue(), leftArm.get());
    }

    private void blend() {
        System.arraycopy(pose, 0, blendFrom, 0, Anim.CHANNELS);
        blendStart = System.nanoTime();
    }

    private void startDeploy() {
        if (shouldHold()) play(type.get().deploy);
    }

    private void play(Anim anim) {
        playing = anim;
        playStart = System.nanoTime();
    }

    public float[] pose() {
        boolean holding = shouldHold();
        if (holding && !wasHolding) play(type.get().deploy);
        wasHolding = holding;

        if (playing == type.get().inspect && mc.player != null && mc.player.handSwinging) {
            blend();
            playing = null;
        }

        long now = System.nanoTime();
        float t = playing == null ? 0 : (now - playStart) / 1e9f * (playing == type.get().inspect ? inspectSpeed.get().floatValue() : 1);
        if (playing != null && t >= playing.duration()) playing = null;
        if (playing == null) java.util.Arrays.fill(pose, 0);
        else playing.sample(t, pose);

        float k = (now - blendStart) / 1e9f / BLEND;
        if (k < 1) {
            float e = k * k * k * (k * (6 * k - 15) + 10);
            for (int c = 0; c < Anim.CHANNELS; c++) {
                float from = blendFrom[c];
                if (c >= 3) from = pose[c] - (float) Math.IEEEremainder(pose[c] - from, 360);
                pose[c] = from + (pose[c] - from) * e;
            }
        }

        float s = now / 1e9f;
        System.arraycopy(pose, 0, out, 0, Anim.CHANNELS);
        out[1] += (float) Math.sin(s * 1.6) * 0.006f;
        out[0] += (float) Math.sin(s * 0.8) * 0.003f;
        out[3] += (float) Math.sin(s * 1.6 + 1) * 0.7f;
        return out;
    }

    public static void applyHandPose(MatrixStack m, float[] p, boolean leftHand) {
        float side = leftHand ? -1 : 1;
        m.translate(p[0] * side, p[1], p[2]);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p[4] * side));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p[3]));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p[5] * side));
    }
}
