package bincnpc.gun.client.gui.legacy;

import java.io.IOException;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Native 1.12 input/drawing bridge. Editor layout and local modal ownership stay unchanged. */
public final class GuiCompat {
    private GuiCompat() {}
    public static final class Component {
        private final String text;
        private Component(String text) { this.text = text == null ? "" : text; }
        public static Component literal(String text) { return new Component(text); }
        public static Component empty() { return literal(""); }
        public static Component translatable(String key, Object... args) { return literal(I18n.format(key, args)); }
        public String getString() { return text; }
        @Override public String toString() { return text; }
    }
    public static final class FormattedCharSequence {
        public final String text;
        FormattedCharSequence(String text) { this.text = text; }
    }
    public static final class Font {
        public final FontRenderer nativeFont;
        public Font(FontRenderer font) { nativeFont = font; }
        public static Font current() { return new Font(Minecraft.getMinecraft().fontRenderer); }
        public int width(String text) { return nativeFont.getStringWidth(text); }
        public int width(Component text) { return width(text.getString()); }
        public String plainSubstrByWidth(String text, int width) { return nativeFont.trimStringToWidth(text, width); }
        public List<FormattedCharSequence> split(Component text, int width) {
            List<FormattedCharSequence> result = new ArrayList<>();
            for (String line : nativeFont.listFormattedStringToWidth(text.getString(), Math.max(1, width))) result.add(new FormattedCharSequence(line));
            return result;
        }
    }
    public static final class Language {
        private static final Language INSTANCE = new Language();
        public static Language getInstance() { return INSTANCE; }
        public boolean has(String key) { return I18n.hasKey(key); }
    }
    public interface ClientTooltipPositioner {}
    public static final class DefaultTooltipPositioner implements ClientTooltipPositioner {
        public static final DefaultTooltipPositioner INSTANCE = new DefaultTooltipPositioner();
    }
    public static final class Tooltip {
        private final Component text;
        private Tooltip(Component text) { this.text = text; }
        public static Tooltip create(Component text) { return new Tooltip(text); }
        public List<FormattedCharSequence> toCharSequence(Minecraft minecraft) { return splitTooltip(minecraft, text); }
        public static List<FormattedCharSequence> splitTooltip(Minecraft minecraft, Component text) { return Font.current().split(text, 260); }
    }
    public static final class NarrationElementOutput {}
    public static final class Keys {
        public static final int KEY_ENTER = Keyboard.KEY_RETURN, KEY_KP_ENTER = Keyboard.KEY_NUMPADENTER,
            KEY_SPACE = Keyboard.KEY_SPACE, KEY_TAB = Keyboard.KEY_TAB, KEY_UP = Keyboard.KEY_UP,
            KEY_DOWN = Keyboard.KEY_DOWN, KEY_PAGE_UP = Keyboard.KEY_PRIOR, KEY_PAGE_DOWN = Keyboard.KEY_NEXT,
            KEY_S = Keyboard.KEY_S, KEY_ESCAPE = Keyboard.KEY_ESCAPE;
        public static final int GLFW_MOD_SHIFT = 1, GLFW_MOD_CONTROL = 2, GLFW_MOD_ALT = 4;
        static int modifiers() { return (GuiScreen.isShiftKeyDown() ? GLFW_MOD_SHIFT : 0) | (GuiScreen.isCtrlKeyDown() ? GLFW_MOD_CONTROL : 0) | (GuiScreen.isAltKeyDown() ? GLFW_MOD_ALT : 0); }
    }
    public static final class Pose {
        public void pushPose() { GlStateManager.pushMatrix(); }
        public void popPose() { GlStateManager.popMatrix(); }
        public void translate(double x, double y, double z) { GlStateManager.translate(x, y, z); }
        public void scale(float x, float y, float z) { GlStateManager.scale(x, y, z); }
    }
    public static class GuiGraphics {
        private final Screen screen;
        private final Deque<int[]> scissors = new ArrayDeque<>();
        private final Pose pose = new Pose();
        public GuiGraphics(Screen screen) { this.screen = screen; }
        public Pose pose() { return pose; }
        /** Native item and tooltip renderers leave depth/lighting enabled in 1.12. */
        public void prepare2D() {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.disableLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
            GlStateManager.color(1, 1, 1, 1);
        }
        private void beginFrame() {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.depthMask(true);
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
            prepare2D();
        }
        private void endFrame() {
            scissors.clear(); applyScissor(); prepare2D();
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
        }
        public void flush() { prepare2D(); }
        public void fill(int x1, int y1, int x2, int y2, int color) { prepare2D(); Gui.drawRect(x1, y1, x2, y2, color); }
        public void hLine(int x1, int x2, int y, int color) { fill(Math.min(x1,x2), y, Math.max(x1,x2) + 1, y + 1, color); }
        public void vLine(int x, int y1, int y2, int color) { fill(x, Math.min(y1,y2), x + 1, Math.max(y1,y2) + 1, color); }
        public void renderOutline(int x, int y, int width, int height, int color) { hLine(x,x+width-1,y,color);hLine(x,x+width-1,y+height-1,color);vLine(x,y,y+height-1,color);vLine(x+width-1,y,y+height-1,color); }
        public int drawString(Font font, String text, int x, int y, int color, boolean shadow) { prepare2D(); return font.nativeFont.drawString(text, x, y, color, shadow); }
        public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) { return drawString(font,text.getString(),x,y,color,shadow); }
        public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) { return drawString(font,text.text,x,y,color,shadow); }
        public void renderItem(ItemStack item, int x, int y) {
            if (item == null || item.isEmpty()) return;
            net.minecraft.client.renderer.RenderItem renderer = Minecraft.getMinecraft().getRenderItem();
            float previousZ = renderer.zLevel;
            GlStateManager.pushMatrix();
            try {
                prepare2D();
                GlStateManager.depthMask(true);
                GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
                GlStateManager.enableDepth();
                RenderHelper.enableGUIStandardItemLighting();
                renderer.zLevel = 0;
                renderer.renderItemAndEffectIntoGUI(item, x, y);
            } finally {
                renderer.zLevel = previousZ;
                RenderHelper.disableStandardItemLighting();
                GlStateManager.popMatrix();
                prepare2D();
            }
        }
        public void renderItemDecorations(Font font, ItemStack item, int x, int y) {
            if (item == null || item.isEmpty()) return;
            prepare2D();
            try { Minecraft.getMinecraft().getRenderItem().renderItemOverlayIntoGUI(font.nativeFont, item, x, y, null); }
            finally { prepare2D(); }
        }
        public void renderEntity(int x, int y, int scale, float mouseX, float mouseY, net.minecraft.entity.EntityLivingBase entity) {
            prepare2D();
            GlStateManager.depthMask(true);
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.enableDepth();
            try { net.minecraft.client.gui.inventory.GuiInventory.drawEntityOnScreen(x, y, scale, mouseX, mouseY, entity); }
            finally { prepare2D(); }
        }
        public void renderTooltip(Font font, Component text, int x, int y) { renderTooltip(font,font.split(text,260),DefaultTooltipPositioner.INSTANCE,x,y); }
        public void renderTooltip(Font font, ItemStack item, int x, int y) { screen.nativeTooltip(item.getTooltip(Minecraft.getMinecraft().player, ITooltipFlag.TooltipFlags.NORMAL),x,y); }
        public void renderTooltip(Font font, List<FormattedCharSequence> text, ClientTooltipPositioner positioner, int x, int y) { List<String> lines=new ArrayList<>(); for(FormattedCharSequence line:text)lines.add(line.text); screen.nativeTooltip(lines,x,y); }
        public void enableScissor(int x1,int y1,int x2,int y2) {
            if(!scissors.isEmpty()){int[] parent=scissors.peek();x1=Math.max(x1,parent[0]);y1=Math.max(y1,parent[1]);x2=Math.min(x2,parent[2]);y2=Math.min(y2,parent[3]);}
            scissors.push(new int[]{x1,y1,Math.max(x1,x2),Math.max(y1,y2)}); applyScissor();
        }
        public void disableScissor() { if(!scissors.isEmpty())scissors.pop(); applyScissor(); }
        private void applyScissor() {
            if(scissors.isEmpty()){GL11.glDisable(GL11.GL_SCISSOR_TEST);return;}
            Minecraft mc=Minecraft.getMinecraft();int scale=new ScaledResolution(mc).getScaleFactor();int[] box=scissors.peek();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(box[0]*scale,mc.displayHeight-box[3]*scale,(box[2]-box[0])*scale,(box[3]-box[1])*scale);
        }
    }
    public abstract static class AbstractWidget {
        protected int x,y,width,height;
        public boolean active=true, visible=true;
        protected boolean hovered;
        private boolean focused;
        private Component message;
        private Tooltip tooltip;
        protected AbstractWidget(int x,int y,int width,int height,Component message){this.x=x;this.y=y;this.width=width;this.height=height;this.message=message;}
        public int getX(){return x;} public int getY(){return y;} public int getWidth(){return width;} public int getHeight(){return height;}
        public void setX(int x){this.x=x;} public void setY(int y){this.y=y;} public void setWidth(int width){this.width=width;} public void setHeight(int height){this.height=height;}
        public Component getMessage(){return message;} public void setMessage(Component message){this.message=message;}
        public void setTooltip(Tooltip tooltip){this.tooltip=tooltip;} public Tooltip getTooltip(){return tooltip;}
        public boolean isMouseOver(double mx,double my){return visible && mx>=x && my>=y && mx<x+width && my<y+height;}
        public boolean isHoveredOrFocused(){return hovered||focused;} public boolean isFocused(){return focused;}
        public void setFocused(boolean focused){this.focused=focused;}
        public final void render(GuiGraphics g,int mx,int my,float tick){if(!visible)return;hovered=isMouseOver(mx,my);renderWidget(g,mx,my,tick);if(hovered&&tooltip!=null)g.screen.setTooltipForNextRenderPass(tooltip,DefaultTooltipPositioner.INSTANCE,false);}
        protected abstract void renderWidget(GuiGraphics g,int mx,int my,float tick);
        public void onClick(double x,double y){}
        public boolean mouseClicked(double mx,double my,int button){if(button!=0||!active||!isMouseOver(mx,my))return false;Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK,1));onClick(mx,my);return true;}
        public boolean mouseReleased(double x,double y,int button){return false;}
        public boolean mouseDragged(double x,double y,int button,double dx,double dy){return false;}
        public boolean mouseScrolled(double x,double y,double delta){return false;}
        public boolean keyPressed(int key,int scan,int modifiers){return false;}
        public boolean keyReleased(int key,int scan,int modifiers){return false;}
        public boolean charTyped(char letter,int modifiers){return false;}
        protected void updateWidgetNarration(NarrationElementOutput output){}
        protected void defaultButtonNarrationText(NarrationElementOutput output){}
    }
    public static class EditBox extends AbstractWidget {
        private final GuiTextField field;
        private Component hint=Component.empty();
        private final Font fieldFont;
        private Consumer<String> responder=value->{};
        private Predicate<String> filter=value->true;
        public EditBox(Font font,int x,int y,int width,int height,Component label){super(x,y,width,height,label);fieldFont=font;field=new GuiTextField(0,font.nativeFont,x,y,width,height);}
        public void setValue(String value){field.setText(value);}
        public String getValue(){return field.getText();}
        public void setResponder(Consumer<String> responder){this.responder=responder;}
        public void setFilter(Predicate<String> filter){this.filter=filter;}
        public void setMaxLength(int length){field.setMaxStringLength(length);}
        public void setBordered(boolean value){field.setEnableBackgroundDrawing(value);}
        public void setTextColor(int color){field.setTextColor(color);}
        public void setTextColorUneditable(int color){field.setDisabledTextColour(color);}
        public void setEditable(boolean value){active=value;field.setEnabled(value);}
        public void setCanLoseFocus(boolean value){field.setCanLoseFocus(value);}
        public void setSuggestion(String value){hint=Component.literal(value);}
        public void setHint(Component value){hint=value==null?Component.empty():value;}
        public void tick(){field.updateCursorCounter();}
        @Override public void setFocused(boolean value){super.setFocused(value);field.setFocused(value);}
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float tick){field.x=x;field.y=y;field.width=width;field.height=height;field.setEnabled(active);field.setVisible(visible);g.prepare2D();field.drawTextBox();if(getValue().isEmpty()&&!isFocused())g.drawString(fieldFont,hint.getString(),x,y,0xFF77786D,false);}
        @Override public boolean mouseClicked(double mx,double my,int button){if(!active||!isMouseOver(mx,my)||button!=0)return false;field.mouseClicked((int)mx,(int)my,button);return true;}
        private boolean type(char letter,int key){if(!active||!isFocused())return false;String before=field.getText();boolean used=field.textboxKeyTyped(letter,key);String after=field.getText();if(!filter.test(after)){field.setText(before);return true;}if(!before.equals(after))responder.accept(after);return used;}
        @Override public boolean keyPressed(int key,int scan,int modifiers){return type('\0',key);}
        @Override public boolean charTyped(char letter,int modifiers){return letter>=32&&letter!=127&&type(letter,0);}
    }
    public static class Screen extends GuiScreen {
        protected final Component title;
        protected Minecraft minecraft;
        protected Font font;
        private final List<AbstractWidget> widgets=new ArrayList<>();
        private AbstractWidget focused;
        private boolean dragging;
        private double lastX,lastY;
        private List<FormattedCharSequence> tooltip;
        protected Screen(Component title){this.title=title;}
        @Override public final void initGui(){minecraft=mc;font=new Font(fontRenderer);clearWidgets();init();}
        public final void init(Minecraft minecraft,int width,int height){setWorldAndResolution(minecraft,width,height);}
        public final void resize(Minecraft minecraft,int width,int height){init(minecraft,width,height);}
        protected void init(){}
        public <T extends AbstractWidget>T addRenderableWidget(T widget){widgets.add(widget);return widget;}
        public List<AbstractWidget> children(){return Collections.unmodifiableList(widgets);}
        public void clearWidgets(){setFocused(null);widgets.clear();}
        public void setFocused(AbstractWidget widget){if(focused!=null)focused.setFocused(false);focused=widget;if(widget!=null)widget.setFocused(true);}
        public AbstractWidget getFocused(){return focused;}
        public void setDragging(boolean value){dragging=value;}
        public boolean isDragging(){return dragging;}
        public void render(GuiGraphics g,int mx,int my,float tick){for(AbstractWidget widget:new ArrayList<>(widgets))widget.render(g,mx,my,tick);}
        public void renderWithTooltip(GuiGraphics g,int mx,int my,float tick){tooltip=null;render(g,mx,my,tick);if(tooltip!=null)g.renderTooltip(font,tooltip,DefaultTooltipPositioner.INSTANCE,mx,my);}
        @Override public final void drawScreen(int mx,int my,float tick){tooltip=null;GuiGraphics g=new GuiGraphics(this);g.beginFrame();try{render(g,mx,my,tick);if(tooltip!=null)g.renderTooltip(font,tooltip,DefaultTooltipPositioner.INSTANCE,mx,my);}finally{g.endFrame();}}
        public void tick(){}
        public void added(){}
        public void mouseMoved(double x,double y){}
        @Override public final void updateScreen(){tick();}
        public void removed(){}
        @Override public final void onGuiClosed(){Keyboard.enableRepeatEvents(false);removed();}
        public boolean isPauseScreen(){return false;}
        @Override public final boolean doesGuiPauseGame(){return isPauseScreen();}
        public void onClose(){minecraft.displayGuiScreen(null);}
        public boolean mouseClicked(double mx,double my,int button){for(AbstractWidget widget:new ArrayList<>(widgets))if(widget.mouseClicked(mx,my,button)){setFocused(widget);dragging=button==0;return true;}setFocused(null);return false;}
        @Override protected final void mouseClicked(int mx,int my,int button)throws IOException{lastX=mx;lastY=my;mouseClicked((double)mx,(double)my,button);}
        public boolean mouseReleased(double mx,double my,int button){dragging=false;return focused!=null&&focused.mouseReleased(mx,my,button);}
        @Override protected final void mouseReleased(int mx,int my,int button){mouseReleased((double)mx,(double)my,button);}
        public boolean mouseDragged(double mx,double my,int button,double dx,double dy){return focused!=null&&focused.mouseDragged(mx,my,button,dx,dy);}
        @Override protected final void mouseClickMove(int mx,int my,int button,long elapsed){mouseDragged(mx,my,button,mx-lastX,my-lastY);lastX=mx;lastY=my;}
        public boolean mouseScrolled(double mx,double my,double delta){for(AbstractWidget widget:widgets)if(widget.mouseScrolled(mx,my,delta))return true;return false;}
        @Override public void handleMouseInput()throws IOException{int delta=Mouse.getEventDWheel();if(delta==0)super.handleMouseInput();double x=Mouse.getEventX()*width/(double)mc.displayWidth,y=height-Mouse.getEventY()*height/(double)mc.displayHeight-1;mouseMoved(x,y);if(delta!=0)mouseScrolled(x,y,Math.signum(delta));}
        public boolean keyPressed(int key,int scan,int modifiers){if(key==Keyboard.KEY_ESCAPE){onClose();return true;}if(focused!=null&&focused.keyPressed(key,scan,modifiers))return true;if(key==Keyboard.KEY_TAB){List<AbstractWidget> available=new ArrayList<>();for(AbstractWidget w:widgets)if(w.visible&&w.active)available.add(w);if(!available.isEmpty())setFocused(available.get(Math.floorMod(available.indexOf(focused)+((modifiers&Keys.GLFW_MOD_SHIFT)!=0?-1:1),available.size())));return true;}return false;}
        public boolean keyReleased(int key,int scan,int modifiers){return focused!=null&&focused.keyReleased(key,scan,modifiers);}
        public boolean charTyped(char letter,int modifiers){return focused!=null&&focused.charTyped(letter,modifiers);}
        @Override protected final void keyTyped(char letter,int key)throws IOException{if(!keyPressed(key,key,Keys.modifiers()))charTyped(letter,Keys.modifiers());}
        @Override public void handleKeyboardInput()throws IOException{if(!Keyboard.getEventKeyState()){keyReleased(Keyboard.getEventKey(),Keyboard.getEventKey(),Keys.modifiers());return;}Keyboard.enableRepeatEvents(true);super.handleKeyboardInput();}
        public static boolean hasShiftDown(){return GuiScreen.isShiftKeyDown();}
        public static boolean hasControlDown(){return GuiScreen.isCtrlKeyDown();}
        public void setTooltipForNextRenderPass(List<FormattedCharSequence> lines,ClientTooltipPositioner positioner,boolean force){if(tooltip==null||force)tooltip=new ArrayList<>(lines);}
        public void setTooltipForNextRenderPass(List<FormattedCharSequence> lines){setTooltipForNextRenderPass(lines,DefaultTooltipPositioner.INSTANCE,false);}
        public void setTooltipForNextRenderPass(Tooltip text,ClientTooltipPositioner positioner,boolean force){setTooltipForNextRenderPass(text.toCharSequence(minecraft),positioner,force);}
        protected void setTooltipForNextRenderPass(Component text){setTooltipForNextRenderPass(Tooltip.splitTooltip(minecraft,text));}
        private void nativeTooltip(List<String> lines,int x,int y){GuiGraphics state=new GuiGraphics(this);state.prepare2D();try{drawHoveringText(lines,x,y);}finally{state.prepare2D();}}
    }
}
