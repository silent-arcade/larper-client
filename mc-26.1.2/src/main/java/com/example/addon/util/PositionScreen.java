package com.example.addon.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class PositionScreen extends Screen {
    public interface Placeable {
        int[] drawOverlay(GuiGraphicsExtractor ctx);

        void placeAt(int left, int top, int width, int height, int screenWidth, int screenHeight);

        double getScale();

        void setScale(double scale);
    }

    private static final int HANDLE = 6;
    private static final double MIN_SCALE = 0.5, MAX_SCALE = 3;

    private final Placeable target;
    private final Screen parent;
    private int[] box = {0, 0, 0, 0};
    private boolean dragging, resizing;
    private double grabX, grabY, startScale;
    private int startW;

    public PositionScreen(String name, Placeable target, Screen parent) {
        super(Component.literal("Move " + name));
        this.target = target;
        this.parent = parent;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x60000000);
        ctx.fill(width / 2, 0, width / 2 + 1, height, 0x30FFFFFF);
        ctx.fill(0, height / 2, width, height / 2 + 1, 0x30FFFFFF);
        box = target.drawOverlay(ctx);
        boolean onHandle = resizing || onHandle(mouseX, mouseY);
        boolean hover = dragging || inside(mouseX, mouseY);
        int c = hover || onHandle ? 0xFFFFFFFF : 0xA0FFFFFF;
        ctx.fill(box[0] - 1, box[1] - 1, box[0] + box[2] + 1, box[1], c);
        ctx.fill(box[0] - 1, box[1] + box[3], box[0] + box[2] + 1, box[1] + box[3] + 1, c);
        ctx.fill(box[0] - 1, box[1], box[0], box[1] + box[3], c);
        ctx.fill(box[0] + box[2], box[1], box[0] + box[2] + 1, box[1] + box[3], c);
        int hx = box[0] + box[2], hy = box[1] + box[3];
        ctx.fill(hx - HANDLE + 1, hy - HANDLE + 1, hx + 2, hy + 2, onHandle ? 0xFFFFFFFF : 0xC0FFFFFF);
        String size = String.format("%.0f%%", target.getScale() * 100);
        int labelY = box[1] + box[3] + 14 > height - 30 ? box[1] - 12 : box[1] + box[3] + 4;
        ctx.text(font, size, box[0] + box[2] - font.width(size), Math.max(2, labelY), 0xFFFFFFFF);
        String hint = "Drag to move - drag the corner or scroll to resize - Esc when done";
        ctx.text(font, hint, (width - font.width(hint)) / 2, height - 20, 0xFFFFFFFF);
    }

    private boolean inside(double x, double y) {
        return x >= box[0] && x <= box[0] + box[2] && y >= box[1] && y <= box[1] + box[3];
    }

    private boolean onHandle(double x, double y) {
        int hx = box[0] + box[2], hy = box[1] + box[3];
        return x >= hx - HANDLE - 2 && x <= hx + 4 && y >= hy - HANDLE - 2 && y <= hy + 4;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0 && onHandle(click.x(), click.y())) {
            resizing = true;
            startScale = target.getScale();
            startW = Math.max(1, box[2]);
            grabX = box[0];
            grabY = box[1];
            return true;
        }
        if (click.button() == 0 && inside(click.x(), click.y())) {
            dragging = true;
            grabX = click.x() - box[0];
            grabY = click.y() - box[1];
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (resizing) {
            double wanted = Math.max(8, click.x() - grabX);
            resize(startScale * wanted / startW, (int) grabX, (int) grabY);
            return true;
        }
        if (!dragging) return super.mouseDragged(click, dx, dy);
        int left = (int) Math.max(0, Math.min(width - box[2], click.x() - grabX));
        int top = (int) Math.max(0, Math.min(height - box[3], click.y() - grabY));
        target.placeAt(left, top, box[2], box[3], width, height);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0 || !inside(x, y) && !onHandle(x, y)) return super.mouseScrolled(x, y, horizontal, vertical);
        resize(target.getScale() + (vertical > 0 ? 0.1 : -0.1), box[0], box[1]);
        return true;
    }

    private void resize(double scale, int left, int top) {
        double old = target.getScale(), s = Math.round(Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale)) * 20) / 20.0;
        if (s == old) return;
        int w = (int) Math.round(box[2] * s / old), h = (int) Math.round(box[3] * s / old);
        target.setScale(s);
        target.placeAt(Math.max(0, Math.min(width - w, left)), Math.max(0, Math.min(height - h, top)), w, h, width, height);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        dragging = false;
        resizing = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
