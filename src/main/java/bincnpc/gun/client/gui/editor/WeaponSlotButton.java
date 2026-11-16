package bincnpc.gun.client.gui.editor;

import java.util.function.*;
import net.minecraft.client.Minecraft;
import bincnpc.gun.client.gui.legacy.GuiCompat.GuiGraphics;
import bincnpc.gun.client.gui.legacy.GuiCompat.Component;
import net.minecraft.item.ItemStack;

public final class WeaponSlotButton extends EditorButton {
    private final Supplier<ItemStack> item;
    private final BooleanSupplier equipped;
    private final Runnable equip;
    public WeaponSlotButton(EditorLayout.Box box, Component role, Supplier<ItemStack> item, BooleanSupplier equipped, Runnable clicked) {
        this(box, role, item, equipped, clicked, null);
    }
    public WeaponSlotButton(EditorLayout.Box box, Component role, Supplier<ItemStack> item, BooleanSupplier equipped, Runnable selected, Runnable equip) {
        super(box, role, Kind.NORMAL, selected); this.item = item; this.equipped = equipped; this.equip = equip;
    }
    public ItemStack item() { return item.get(); }
    public EditorLayout.Box itemBounds() {
        boolean labelled = !getMessage().getString().isEmpty();
        int size = Math.max(14, Math.min(labelled ? 30 : 34, Math.min(width - 4, height - (labelled ? 17 : 4))));
        return new EditorLayout.Box(getX() + (width - size) / 2, getY() + (labelled ? 3 : (height - size) / 2), size, size);
    }
    @Override public void onClick(double x, double y) {
        if (equip != null && itemBounds().contains(x, y)) equip.run(); else super.onClick(x, y);
    }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        bincnpc.gun.client.gui.legacy.GuiCompat.Font font = bincnpc.gun.client.gui.legacy.GuiCompat.Font.current();
        EditorStyle.panel(g, new EditorLayout.Box(getX(), getY(), width, height), selected ? EditorStyle.HOVER : isHoveredOrFocused() ? 0xFFE2E6D6 : EditorStyle.PAPER);
        if (selected || isFocused()) g.renderOutline(getX(), getY(), width, height, selected ? EditorStyle.GREEN : EditorStyle.GOLD);
        boolean labelled = !getMessage().getString().isEmpty();
        EditorLayout.Box inner = itemBounds(); int size = inner.width(), slotX = inner.x(), slotY = inner.y();
        EditorStyle.slot(g, slotX, slotY, size, selected);
        ItemStack stack = item.get();
        int iconSize = Math.min(28, size - 4), itemX = slotX + (size - iconSize) / 2, itemY = slotY + (size - iconSize) / 2;
        if (!stack.isEmpty()) {
            g.pose().pushPose(); g.pose().translate(itemX, itemY, 0); g.pose().scale(iconSize / 16F, iconSize / 16F, 1);
            g.renderItem(stack, 0, 0); g.renderItemDecorations(font, stack, 0, 0); g.pose().popPose();
        }
        else { g.hLine(slotX + size / 2 - 3, slotX + size / 2 + 3, slotY + size / 2, EditorStyle.MUTED); g.vLine(slotX + size / 2, slotY + size / 2 - 3, slotY + size / 2 + 3, EditorStyle.MUTED); }
        if (equipped.getAsBoolean()) g.fill(getX() + width - 7, getY() + 3, getX() + width - 3, getY() + 7, EditorStyle.GREEN);
        if (labelled) { String text = EditorStyle.clipped(font, getMessage().getString(), width - 6); g.drawString(font, text, getX() + (width - font.width(text)) / 2, getY() + height - 12, EditorStyle.INK, false); }
    }
}
