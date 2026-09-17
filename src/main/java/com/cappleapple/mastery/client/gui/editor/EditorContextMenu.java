package com.cappleapple.mastery.client.gui.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import java.util.List;

/** Canvas-local popup; coordinates are clamped so every action remains on screen. */
public final class EditorContextMenu {
    public record Action(String label, Runnable run, boolean destructive) {}
    private static final int ROW = 21, WIDTH = 166;
    private final List<Action> actions;
    private final int x, y;
    private int selected;
    public EditorContextMenu(int mouseX, int mouseY, int screenWidth, int screenHeight, List<Action> actions) {
        this.actions = List.copyOf(actions);
        x = Math.max(4, Math.min(mouseX, screenWidth - WIDTH - 4));
        y = Math.max(4, Math.min(mouseY, screenHeight - actions.size() * ROW - 8));
    }
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 300);
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + actions.size() * ROW + 1, 0xFFE5D2AA);
        graphics.fill(x, y, x + WIDTH, y + actions.size() * ROW, 0xFF292923);
        int hovered = row(mouseX, mouseY);
        if (hovered >= 0) selected = hovered;
        for (int i = 0; i < actions.size(); i++) {
            if (i == selected) graphics.fill(x + 1, y + i * ROW + 1, x + WIDTH - 1, y + (i + 1) * ROW - 1, 0xFF55513E);
            Action action = actions.get(i);
            graphics.drawString(font, action.label(), x + 8, y + i * ROW + 6, action.destructive() ? 0xFF9999 : 0xF1E7CF);
        }
        graphics.pose().popPose();
    }
    public void click(double mouseX, double mouseY, int button) {
        int index = row(mouseX, mouseY);
        if (button == 0 && index >= 0) actions.get(index).run().run();
    }
    public boolean key(int key) {
        if (key == GLFW.GLFW_KEY_UP) selected = Math.floorMod(selected - 1, actions.size());
        else if (key == GLFW.GLFW_KEY_DOWN) selected = (selected + 1) % actions.size();
        else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { actions.get(selected).run().run(); return true; }
        else if (key == GLFW.GLFW_KEY_ESCAPE) return true;
        return false;
    }
    private int row(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + actions.size() * ROW ? (int)(mouseY - y) / ROW : -1;
    }
}
