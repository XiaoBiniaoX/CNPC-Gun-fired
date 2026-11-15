package bincnpc.gun;

import bincnpc.gun.client.gui.editor.*;
import bincnpc.gun.client.gui.editor.EditorLayout.*;
import com.google.gson.*;
import java.util.*;

/** Viewport and draft regression checks without starting Minecraft or an OpenGL context. */
public final class EditorChecks {
    private static int checks;
    public static void main(String[] args) { run(); }
    public static void run() {
        checks = 0; viewports(); menu(); drafts(); graphs();
        System.out.println("CNPCGUN_EDITOR_CHECKS=" + checks + " PASSED");
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void rejected(Runnable operation, String message) {
        checks++; try { operation.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError(message);
    }
    private static boolean inside(Box outer, Box inner) {
        return inner.width() > 0 && inner.height() > 0 && inner.x() >= outer.x() && inner.y() >= outer.y()
            && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }
    private static boolean separate(List<Box> boxes) {
        for (int i = 0; i < boxes.size(); i++) for (int j = i + 1; j < boxes.size(); j++) if (boxes.get(i).intersects(boxes.get(j))) return false;
        return true;
    }
    private static void viewports() {
        for (int[] size : new int[][]{{320,240},{427,240},{640,360},{854,480},{960,540},{1280,720}}) {
            int w = size[0], h = size[1]; EditorLayout layout = new EditorLayout(w, h); Box screen = new Box(0,0,w,h);
            check(inside(screen, layout.header) && inside(screen, layout.footer) && inside(screen, layout.workspace) && inside(screen, layout.sidebar), "fullscreen panels within viewport");
            check(separate(List.of(layout.header, layout.sidebar, layout.workspace, layout.footer)), "panel regions do not overlap");
            check(inside(layout.header, new Box(8,7,84,20)) && inside(layout.footer, layout.saveButton()) && inside(layout.footer, layout.discardButton())
                && !layout.saveButton().intersects(layout.discardButton()), "return and save remain reachable");
            List<Box> navigation = new ArrayList<>(), slots = new ArrayList<>();
            for (int i = 0; i < 7; i++) navigation.add(layout.navigation(i));
            for (int i = 0; i < 6; i++) slots.add(layout.weaponSlot(i));
            check(navigation.stream().allMatch(box -> inside(layout.sidebar, box)) && separate(navigation), "three drawers and their expanded items fit");
            check(slots.stream().allMatch(box -> inside(layout.body, box) && box.width() >= 26) && separate(slots), "six labelled weapon slots fit");
            check(slots.stream().noneMatch(box -> box.intersects(layout.weaponForm())), "slot cards are separate from switch rules");
            check(inside(layout.body, layout.weaponForm()) && layout.weaponForm().height() >= 58, "switch rules have a scrollable viewport");
            check(inside(layout.body, layout.listPane()) && inside(layout.body, layout.detailPane()) && !layout.listPane().intersects(layout.detailPane()), "master and detail geometry");
            Box dialog = EditorLayout.dialog(w,h), content = EditorLayout.dialogContent(dialog);
            check(inside(screen, dialog) && inside(dialog, content), "dialog stays on screen");
            List<InventorySlot> inventory = EditorLayout.inventorySlots(content);
            Set<Integer> indexes = new HashSet<>(); inventory.forEach(slot -> indexes.add(slot.index()));
            check(inventory.size() == 37 && indexes.size() == 37 && indexes.contains(40) && indexes.containsAll(java.util.stream.IntStream.range(0,36).boxed().toList()), "inventory maps all main and offhand slots once");
            check(inventory.stream().allMatch(slot -> inside(content, slot.box()) && slot.box().width() >= 22), "inventory icons and offhand stay within content");
            check(separate(inventory.stream().map(InventorySlot::box).toList()), "inventory source slots do not overlap");
            check(inventory.stream().noneMatch(slot -> slot.box().intersects(new Box(dialog.x()+10,dialog.bottom()-28,dialog.width()-20,20))), "inventory cannot cover dialog buttons");
            form(layout.weaponForm(), true);
            form(layout.body, false);
            form(content, false);
            form(new Box(content.x()+104,content.y(),content.width()-104,content.height()-24), false);
        }
        check(EditorLayout.clampScroll(-100,300,60) == 0 && EditorLayout.clampScroll(999,300,60) == 240 && EditorLayout.clampScroll(999,10,60) == 0, "scroll bounds include short forms");
    }
    private static void form(Box view, boolean compact) {
        List<FormField> fields = List.of(new FormField(1,true), new FormField(1,false), new FormField(1,true),
            new FormField(0,false), new FormField(2,true), new FormField(3,true), new FormField(1,true), new FormField(1,false));
        FormGrid grid = EditorLayout.formGrid(view, fields, compact, 0);
        check(grid.cells().size() == fields.size() && separate(grid.cells().stream().map(FormCell::row).toList()), "headings and component rows do not collide");
        boolean valid = true;
        for (int i = 0; i < grid.cells().size(); i++) {
            FormCell cell = grid.cells().get(i);
            for (Box input : cell.controls()) {
                Box rendered = fields.get(i).text() ? new Box(input.x()-4,input.y()-4,input.width()+8,input.height()+8) : input;
                valid &= inside(cell.row(), rendered) && input.width() >= 28;
            }
            valid &= separate(cell.controls());
        }
        check(valid, "form controls and their borders fit their rows");
        check(grid.cells().get(4).row().width() == view.width()-12 && grid.cells().get(5).row().width() == view.width()-12, "ranges and vectors use the full form width");
        int offset = EditorLayout.clampScroll(9999,grid.totalHeight(),view.height()-8);
        FormGrid scrolled = EditorLayout.formGrid(view,fields,compact,offset);
        check(scrolled.cells().get(scrolled.cells().size()-1).row().bottom() <= view.bottom()-4, "last form row is fully reachable");
    }
    private static Map<Integer, MenuTabLayout.Position> positions(List<MenuTabLayout.Tab> input, int top, int width) {
        Map<Integer,MenuTabLayout.Position> result = new HashMap<>();
        for (MenuTabLayout.Position p : MenuTabLayout.arrange(input,87,4,top,width)) result.put(p.id(),p);
        return result;
    }
    private static void menu() {
        for (int[] widths : new int[][]{{48,48,22,39,48,48,48,48,18,30},{46,38,24,62,60,40,38,36,18,42}}) {
            int[] ids = {1,2,3,4,5,6,7,87,0,66}; List<MenuTabLayout.Tab> first = new ArrayList<>();
            for (int i=0;i<ids.length;i++) first.add(new MenuTabLayout.Tab(ids[i],widths[i]));
            List<MenuTabLayout.Tab> second = new ArrayList<>(first); Collections.swap(second,6,7);
            Map<Integer,MenuTabLayout.Position> a = positions(first,20,420), b = positions(second,20,420);
            check(a.equals(b) && a.size() == 10, "magic and gun injection order gives the same header");
            check(a.get(87).x() >= a.get(7).x()+widths[6] && a.get(87).y() == a.get(7).y(), "weapons follows magic without overlap");
            List<Box> boxes = new ArrayList<>();
            for (int i=0;i<ids.length;i++) { MenuTabLayout.Position p=a.get(ids[i]); boxes.add(new Box(p.x(),p.y(),widths[i],25)); }
            check(separate(boxes) && boxes.stream().allMatch(box -> inside(new Box(4,0,420,48),box)), "Chinese and English header fits above the original panel");
            check(a.get(0).y() == 3 && a.get(66).y() == 3, "close and delete remain at the top");
        }
        List<MenuTabLayout.Tab> overflow = List.of(new MenuTabLayout.Tab(1,120),new MenuTabLayout.Tab(2,120),new MenuTabLayout.Tab(7,120),new MenuTabLayout.Tab(87,48),new MenuTabLayout.Tab(0,18));
        Map<Integer,MenuTabLayout.Position> rows = positions(overflow,64,420);
        check(rows.get(1).y() >= 0 && rows.get(87).y()-rows.get(1).y() >= 26, "wrapped rows leave room for the selected native tab");
    }
    private static void drafts() {
        EditorDraft draft = new EditorDraft(); JsonObject base = new JsonObject();
        for (int i=0;i<70;i++) base.addProperty("setting."+i,0);
        for (String id : List.of("gun.minRange","gun.range","gun.minInterval","gun.maxInterval","gun.reloadMinTicks","gun.reloadMaxTicks","ammo.minimumReserve","ammo.reserveLimit")) base.addProperty(id,0);
        for (int slot=0;slot<3;slot++) { base.addProperty("throw.slot"+slot+".minRange",0); base.addProperty("throw.slot"+slot+".maxRange",0); }
        List<String> slotFields = List.of("priority","minRangeEnabled","minRange","maxRangeEnabled","maxRange","maxHealthEnabled","maxHealth");
        for (int slot=0;slot<6;slot++) for (String key : slotFields) {
            if (key.endsWith("Enabled")) base.addProperty("weapon.slot"+slot+"."+key,false);
            else base.addProperty("weapon.slot"+slot+"."+key,0);
        }
        for (int i=0;i<31;i++) draft.set(base,"setting."+i,new JsonPrimitive(1));
        draft.set(base,"gun.minRange",new JsonPrimitive(1));
        for (int i=31;i<70;i++) draft.set(base,"setting."+i,new JsonPrimitive(1));
        for (String id : base.keySet()) if (!id.startsWith("setting.")) draft.set(base,id,
            base.get(id).getAsJsonPrimitive().isBoolean() ? new JsonPrimitive(true) : new JsonPrimitive(1));
        List<JsonObject> batches = draft.batches(32); Set<String> submittedKeys = new HashSet<>(); Map<String,Integer> batchFor = new HashMap<>();
        for (int i=0;i<batches.size();i++) {
            JsonObject batch = batches.get(i); check(batch.size() > 0 && batch.size() <= 32, "save batch respects the server limit");
            for (String key : batch.keySet()) { check(submittedKeys.add(key), "each changed field is submitted once"); batchFor.put(key,i); }
        }
        check(submittedKeys.equals(base.keySet()), "batching retains every edited field");
        check(batchFor.get("gun.minRange").equals(batchFor.get("gun.range")) && batchFor.get("gun.minInterval").equals(batchFor.get("gun.maxInterval"))
            && batchFor.get("ammo.minimumReserve").equals(batchFor.get("ammo.reserveLimit")), "dependent ranges stay in one transaction");
        for (int slot=0;slot<3;slot++) check(batchFor.get("throw.slot"+slot+".minRange").equals(batchFor.get("throw.slot"+slot+".maxRange")), "throwing distance bounds are saved atomically");
        check(batchFor.get("gun.reloadMinTicks").equals(batchFor.get("gun.reloadMaxTicks")), "reload duration bounds are saved atomically");
        for (int slot=0;slot<6;slot++) {
            String prefix="weapon.slot"+slot+".";
            check(slotFields.stream().map(key -> batchFor.get(prefix+key)).allMatch(batchFor.get(prefix+"priority")::equals), "all settings and switches for a weapon slot stay together");
        }
        JsonObject first=batches.get(0); String editedAgain=first.keySet().iterator().next();
        draft.set(base,editedAgain,new JsonPrimitive(2)); draft.acknowledge(first);
        check(draft.value(base,editedAgain).getAsInt() == 2 && draft.size() == base.size()-first.size()+1, "acknowledgement retains edits made after submission");
        JsonObject snapshot=draft.snapshot(); snapshot.addProperty(editedAgain,99);
        check(draft.value(base,editedAgain).getAsInt() == 2 && base.get(editedAgain).getAsInt() == 0, "draft snapshots cannot mutate draft or server data");
        draft.set(base,editedAgain,new JsonPrimitive(0)); check(!draft.snapshot().has(editedAgain), "restoring the server value clears the change");
        draft.clear(); check(!draft.dirty() && draft.batches(32).isEmpty(), "discard leaves no save work");
        check(!draft.value(null,"gun.reloadSpeedEnabled").getAsBoolean() && draft.value(new JsonObject(),"gun.reloadMinTicks").getAsInt() == 30,
            "old editor snapshots recover missing reload controls from schema defaults");
        JsonObject missing = new JsonObject(); missing.add("gun.reloadMaxTicks",JsonNull.INSTANCE);
        check(draft.value(missing,"gun.reloadMaxTicks").getAsInt() == 60 && draft.value(null,"unknown.option").isJsonNull(), "null values use known defaults without inventing unknown fields");
    }
    private static JsonObject sample() {
        return JsonParser.parseString("""
            {"id":"combo","entryNode":"start","future":{"value":3},"nodes":[
              {"id":"start","action":"slashblade:test/first","onSuccess":"target","onFailure":"fallback","onTimeout":"wait"},
              {"id":"target","action":"slashblade:test/last","parameters":{"count":2}},
              {"id":"fallback","action":"slashblade:test/fallback","onSuccess":"target","onTimeout":"target"},
              {"id":"wait","action":"slashblade:test/wait","onFailure":"target"}]}
            """).getAsJsonObject();
    }
    private static void graphs() {
        JsonObject source=sample(); GraphDraft draft=new GraphDraft(source,"combo"); draft.validateEdges();
        source.getAsJsonObject("future").addProperty("value",9);
        check(draft.graph.getAsJsonObject("future").get("value").getAsInt()==3 && !draft.dirty(), "stored graph is copied and unknown data is retained");
        check(draft.node("wait").get("onTimeout").getAsString().equals("target"), "legacy timeout inherits the failure branch");
        draft.positions.put("target",new GraphDraft.Point(20,30));
        JsonObject target=draft.node("target").deepCopy(); target.addProperty("id","finish"); draft.update("target",target);
        check(draft.node("start").get("onSuccess").getAsString().equals("finish") && draft.node("wait").get("onFailure").getAsString().equals("finish")
            && draft.node("fallback").get("onTimeout").getAsString().equals("finish"), "renaming preserves success, failure and timeout references");
        check(draft.positions.containsKey("finish") && !draft.positions.containsKey("target") && !draft.graph.has("positions"), "canvas positions follow rename without entering NPC data");
        JsonObject entry=draft.node("start").deepCopy(); entry.addProperty("id","opening"); draft.update("start",entry);
        check(draft.graph.get("entryNode").getAsString().equals("opening"), "entry node follows rename");
        JsonObject before=draft.graph.deepCopy(), bad=draft.node("finish").deepCopy(); bad.addProperty("onSuccess","opening");
        rejected(() -> draft.update("finish",bad), "cyclic edit rejected"); check(draft.graph.equals(before), "failed cyclic edit rolls back the whole graph");
        bad.addProperty("onSuccess","absent"); rejected(() -> draft.update("finish",bad), "dangling edit rejected"); check(draft.graph.equals(before), "failed dangling edit rolls back");
        JsonObject duplicate=draft.node("finish").deepCopy(); duplicate.addProperty("id","opening"); rejected(() -> draft.update("finish",duplicate), "duplicate node names rejected");
        String copy=draft.copy("finish"); draft.node(copy).getAsJsonObject("parameters").addProperty("count",4);
        check(draft.node("finish").getAsJsonObject("parameters").get("count").getAsInt()==2, "copying a node isolates parameter edits");
        Map<String,JsonObject> unchanged=new HashMap<>(); for(String id:draft.ids()) unchanged.put(id,draft.node(id).deepCopy());
        List<String> order=draft.ids(); draft.reorder(copy,-1);
        check(!draft.ids().equals(order) && draft.ids().stream().allMatch(id -> draft.node(id).equals(unchanged.get(id))), "display order changes do not rewrite connections");
        draft.graph.addProperty("entryNode","finish"); draft.remove("finish");
        check(draft.graph.get("entryNode").getAsString().equals(draft.ids().get(0)), "deleting the entry chooses an existing node");
        check(draft.node("opening").get("onSuccess").getAsString().equals("end") && draft.node("wait").get("onFailure").getAsString().equals("end")
            && draft.node("fallback").get("onTimeout").getAsString().equals("end"), "deleting a target clears all branch types");
        draft.validateEdges(); JsonObject saved=draft.graph.deepCopy(); draft.graph.addProperty("note","later edit"); draft.acknowledge(saved);
        check(draft.dirty(), "save acknowledgement does not discard a newer graph edit"); draft.acknowledge(draft.graph); check(!draft.dirty(), "current graph acknowledgement clears dirty state");
        GraphDraft invalid=new GraphDraft(sample(),"combo"); invalid.graph.addProperty("entryNode","absent"); rejected(invalid::validateEdges,"entry target must exist");
        GraphDraft fresh=new GraphDraft(null,"new_combo"); String a=fresh.add("slashblade:test/first"), b=fresh.add("slashblade:test/second");
        check(fresh.dirty() && fresh.graph.get("entryNode").getAsString().equals(a) && fresh.node(a).get("onSuccess").getAsString().equals(b), "new nodes create a usable sequential combo");
        fresh.remove(a); fresh.remove(b); check(fresh.nodes().isEmpty() && fresh.graph.get("entryNode").getAsString().isEmpty(), "removing the last node leaves no dangling entry");
        JsonObject broken=sample(); broken.add("nodes",JsonNull.INSTANCE); JsonObject retained=broken.deepCopy();
        rejected(() -> new GraphDraft(broken,"combo"), "null node collections are reported instead of silently erasing a combo");
        check(broken.equals(retained), "rejected graph data remains available for recovery");
        JsonObject optional=sample(); optional.getAsJsonArray("nodes").get(0).getAsJsonObject().add("conditions",JsonNull.INSTANCE);
        GraphDraft recovered=new GraphDraft(optional,"combo");
        check(recovered.node("start").getAsJsonArray("conditions").isEmpty() && optional.getAsJsonArray("nodes").get(0).getAsJsonObject().get("conditions").isJsonNull(),
            "null optional conditions are normalized only in the copied editor draft");
        JsonObject missingAction=sample(); missingAction.getAsJsonArray("nodes").get(0).getAsJsonObject().remove("action");
        rejected(() -> new GraphDraft(missingAction,"combo"), "missing node action is reported without a null dereference");
    }
}
