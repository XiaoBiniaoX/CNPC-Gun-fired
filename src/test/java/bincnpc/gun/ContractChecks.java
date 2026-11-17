package bincnpc.gun;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.CombatDecisions;
import bincnpc.gun.common.combat.Scheduler;
import bincnpc.gun.common.combat.Prefire;
import bincnpc.gun.common.config.*;
import bincnpc.gun.slashblade.action.*;
import bincnpc.gun.slashblade.combo.*;
import bincnpc.gun.slashblade.strategy.Presets;
import bincnpc.gun.throwing.Ballistics;
import bincnpc.gun.throwing.ThrowPolicy;
import bincnpc.gun.gun.controller.ReloadTiming;
import bincnpc.gun.gun.controller.ReloadPolicy;
import bincnpc.gun.gun.ammo.AmmoPolicy;
import bincnpc.gun.gun.controller.RecoilState;
import bincnpc.gun.gun.controller.FireTiming;
import bincnpc.gun.compat.superb.BocekDrawState;
import com.google.gson.*;
import java.util.*;

/** Pure contract checks; this task never initializes a Minecraft server or client. */
public final class ContractChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        json(); schema(); graphs(); scheduling(); combatDecisions(); ballistics(); throwingPolicy(); reloadTiming(); ammunitionPolicy(); recoil(); firing(); bowDraw(); presets(); api();
        EditorChecks.run();
        System.out.println("CNPCGUN_CONTRACT_CHECKS=" + checks + " PASSED");
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void rejected(Runnable operation, String message) {
        checks++; try { operation.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError(message);
    }
    private static void json() {
        JsonObject source = SafeJson.object(Map.of("name", "NPC", "nodes", List.of(Map.of("id", "n1"))));
        JsonObject copy = SafeJson.object(source); copy.addProperty("name", "changed");
        check(source.get("name").getAsString().equals("NPC"), "defensive JSON copy");
        check(((Map<?, ?>)SafeJson.plain(source)).get("nodes") instanceof Object[], "ES5 arrays");
        check(SafeJson.copy(Map.of("0", "first", "1", "second")).isJsonArray(), "Nashorn indexed map");
        rejected(() -> SafeJson.copy(Map.of("0", "first", "2", "third")), "sparse arrays rejected");
        rejected(() -> SafeJson.copy(new Object()), "arbitrary host object rejected");
        rejected(() -> SafeJson.copy(Double.NaN), "NaN rejected");
        rejected(() -> SafeJson.copy("a".repeat(4097)), "string bound");
        rejected(() -> SafeJson.parseObject("{"), "malformed JSON");
        rejected(() -> SafeJson.parseObject("{\"a\":" + "[".repeat(500) + "0" + "]".repeat(500) + "}"), "nesting checked before parser");
        check(SafeJson.parseObject("{\"a\":\"[\\\"{\\\"}]\"}").get("a").getAsString().contains("{"), "quoted braces do not change depth");
        Map<String, Object> oversized = new LinkedHashMap<>(); for (int i = 0; i < 100; i++) oversized.put("field" + i, "中".repeat(1000));
        rejected(() -> SafeJson.object(oversized), "UTF-8 request byte bound");
        JsonObject stored = SafeJson.persistedObject("{\"future\":\"" + "z".repeat(100000) + "\"}");
        check(stored.get("future").getAsString().length() == 100000, "stored aggregate limit differs from individual edit limit");
        check(SafeJson.objectField(null, "parameters").size() == 0 && SafeJson.arrayField(null, "nodes").isEmpty(), "absent JSON containers have empty typed defaults");
        JsonObject nulls = SafeJson.parseObject("{\"parameters\":null,\"nodes\":null,\"flag\":null,\"count\":\"3\"}");
        check(SafeJson.objectField(nulls, "parameters").size() == 0 && SafeJson.arrayField(nulls, "nodes").isEmpty()
            && !SafeJson.bool(nulls, "flag", false) && SafeJson.integer(nulls, "count", 7) == 7, "null and mistyped JSON values do not escape typed fallbacks");
    }
    private static void schema() {
        Set<String> ids = new HashSet<>();
        for (Parameter p : ConfigSchema.all()) {
            check(ids.add(p.id), "duplicate configuration key"); p.validate(SafeJson.copy(p.defaultValue));
            if (ConfigSchema.isGunSetting(p.id)) check(p.capability.isEmpty(), "gun settings remain editable for every native weapon: " + p.id);
        }
        Views.GunCaps zero = new Views.GunCaps().withNpcControls();
        check(zero.canControlRecoil && zero.canControlSpread && zero.canControlSpreadLimit && zero.canBlindFire,
            "zero native recoil and spread do not remove NPC controls");
        check(!zero.hasRecoil && !zero.hasSpread && !zero.hasMagazine && !zero.canReload,
            "NPC control settings do not invent native weapon resources or magazine mechanics");
        rejected(() -> Parameter.integer("count", 1, 0, 10).validate(new JsonPrimitive(1.5)), "fractional count");
        rejected(() -> Parameter.bool("enabled", true).validate(new JsonPrimitive("true")), "boolean must be boolean");
        rejected(() -> ConfigSchema.get("slashblade.preset").validate(null), "missing preset value rejected");
        rejected(() -> new Parameter("broken", "number", 0, null, 10.0, null, "").validate(new JsonPrimitive(1)), "missing numeric bounds rejected without unboxing null");
        check(!(boolean)ConfigSchema.get("gun.reloadSpeedEnabled").defaultValue, "reload speed overrides are disabled by default");
        rejected(() -> ConfigSchema.get("gun.accuracy").validate(new JsonPrimitive(-1)), "accuracy lower bound");
        rejected(() -> ConfigSchema.get("gun.accuracy").validate(new JsonPrimitive(101)), "accuracy upper bound");
        rejected(() -> ConfigSchema.get("gun.prefire").validate(new JsonPrimitive("ANY_TARGET")), "prefire mode whitelist");
        rejected(() -> ConfigSchema.get("weapon.primarySlot").validate(new JsonPrimitive(6)), "primary slot upper bound");
        rejected(() -> Parameter.bool("replace", false).validate(new JsonPrimitive("false")), "RPC replacement flag is a boolean");
        rejected(() -> Parameter.number("range", 1, 0, 10).validate(new JsonPrimitive(11)), "numeric range");
        rejected(() -> Parameter.choice("mode", "A", "A", "B").validate(new JsonPrimitive("C")), "enum whitelist");
        Parameter vector = new Parameter("vector", "vector", new double[]{0, 0, 0}, -10.0, 10.0, null, "");
        rejected(() -> vector.validate(JsonParser.parseString("[1,2,\"3\"]")), "numeric vector type");
        Parameter range = new Parameter("range", "range", new double[]{0, 1}, 0.0, 10.0, null, "");
        rejected(() -> range.validate(JsonParser.parseString("[4,2]")), "inverted range");
        NpcConfig config = new NpcConfig(SafeJson.parseObject("{\"futureField\":{\"v\":7},\"values\":{\"future.option\":12,\"gun.fireInterval\":-1}}"));
        check(config.integer("gun.fireInterval") == 4, "invalid stored value uses schema default");
        config.set("gun.fireInterval", new JsonPrimitive(6));
        JsonObject view = config.snapshot(); view.addProperty("futureField", "mutated");
        check(config.snapshot().getAsJsonObject("futureField").get("v").getAsInt() == 7, "unknown fields preserved without aliasing");
        check(config.snapshot().getAsJsonObject("values").get("future.option").getAsInt() == 12, "unknown configuration values preserved");
        check(config.revision == 1, "revision changes on committed edit");
        check(config.get("unknown").isJsonNull(), "unknown query key");
        NpcConfig legacy = new NpcConfig(SafeJson.parseObject("{\"schemaVersion\":0,\"gun.fireInterval\":7,\"futureField\":9}"));
        check(legacy.integer("gun.fireInterval") == 7 && legacy.snapshot().get("schemaVersion").getAsInt() == NpcConfig.SCHEMA, "schema zero flat fields migrate");
        check(legacy.snapshot().get("futureField").getAsInt() == 9, "migration retains unknown fields");
        NpcConfig ranges = new NpcConfig(SafeJson.parseObject("{\"values\":{\"gun.minRange\":60,\"gun.range\":20,\"ammo.minimumReserve\":200,\"ammo.reserveLimit\":100}}"));
        check(ranges.number("gun.minRange") <= ranges.number("gun.range") && ranges.integer("ammo.minimumReserve") <= ranges.integer("ammo.reserveLimit"), "stored ranges recover to valid pairs");
        check(ranges.snapshot().getAsJsonObject("recoveredRanges").get("gun.minRange").getAsInt() == 60, "invalid original range retained for recovery");
        check(new NpcConfig(SafeJson.parseObject("{\"schemaVersion\":2,\"futureField\":8}")).snapshot().get("schemaVersion").getAsInt() == 2, "future schema version retained");
    }
    private static ActionRegistry.Definition definition(String id, String type, String... tags) {
        return new ActionRegistry.Definition(new Views.ActionInfo(id, type, "tests", "tests.action", "tests.description", 20, 3,
            5, new Parameter[]{Parameter.integer("count", 1, 1, 16)}, true, "", true, type.equals("PHANTOM_SWORD"), true), "tests", "", tags);
    }
    private static JsonObject graph() {
        return SafeJson.parseObject("{\"id\":\"test\",\"entryNode\":\"start\",\"nodes\":["
            + "{\"id\":\"start\",\"action\":\"slashblade:tests/basic\",\"parameters\":{},\"conditions\":[{\"id\":\"distance\",\"min\":0,\"max\":4}],\"onSuccess\":\"near\",\"onFailure\":\"far\"},"
            + "{\"id\":\"near\",\"action\":\"slashblade:tests/basic\",\"onSuccess\":\"end\"},"
            + "{\"id\":\"far\",\"action\":\"slashblade:tests/basic\",\"onSuccess\":\"end\"}]}" );
    }
    private static void recoil() {
        RecoilState state = new RecoilState();
        state.kick(10, 4); check(state.pitch() == -4, "successful shot kicks upward");
        state.advance(11, true); check(state.pitch() == -3, "recoil recovers with elapsed server ticks");
        state.advance(11, true); check(state.pitch() == -3, "same tick cannot recover recoil twice");
        state.kick(11, 2); check(state.pitch() == -5, "multiple shots accumulate once each");
        state.kick(11, Double.NaN); state.kick(11, Double.POSITIVE_INFINITY);
        check(state.pitch() == -5, "invalid native recoil cannot corrupt NPC aiming");
        state.kick(11, 100000); check(state.pitch() == -30, "recoil remains bounded");
        state.advance(12, false); check(state.pitch() == 0, "disabling recoil immediately clears its effect");
        state.kick(12, 3); state.advance(9, true); check(state.pitch() == 0, "recoil clears when server time rolls back");
        state.kick(9, 3); state.advance(1000, true); check(state.pitch() == 0, "long unload gaps do not retain recoil");
        state.kick(1000, 3); state.reset(); check(state.pitch() == 0, "weapon and target changes release recoil state");
    }
    private static void firing() {
        for (String mode : List.of("TRACK", "SWEEP")) {
            check(!Prefire.active(mode, false, false), "prefire cannot acquire an unseen target");
            check(!Prefire.active(mode, false, true), "prefire needs a locked target");
            check(!Prefire.active(mode, true, true), "visible targets retain ordinary aim and spread");
            check(Prefire.active(mode, true, false), "prefire applies to a locked target behind cover");
        }
        check(!Prefire.active("NONE", true, false) && !Prefire.active("ANY_TARGET", true, false), "disabled and unknown prefire modes cannot fire through cover");

        NpcConfig config = new NpcConfig(new JsonObject());
        config.set("gun.fireInterval", new JsonPrimitive(1));
        FireTiming.Settings nativeSettings = FireTiming.Settings.read(config);
        check(FireTiming.plan(nativeSettings, 6, true, true, 0, 1).ticks() == 6, "native mode retains the weapon interval without an override");
        config.set("gun.overrideNativeRate", new JsonPrimitive(true));
        check(FireTiming.plan(FireTiming.Settings.read(config), 6, true, true, 0, 1).ticks() == 1, "native mode can explicitly override a slow native gun");
        config.set("gun.firingMode", new JsonPrimitive("AUTO"));
        check(FireTiming.plan(FireTiming.Settings.read(config), 6, true, true, 0, 1).ticks() == 1, "automatic override uses the configured interval");
        config.set("gun.overrideNativeRate", new JsonPrimitive(false));
        check(FireTiming.plan(FireTiming.Settings.read(config), 6, true, true, 0, 1).ticks() == 6, "custom triggers wait for the native interval rather than repeatedly rejecting shots");
        config.set("gun.fireInterval", new JsonPrimitive(12));
        check(FireTiming.plan(FireTiming.Settings.read(config), 6, true, true, 0, 1).ticks() == 12, "custom rate can slow a native weapon without overriding it");
        config.set("gun.overrideNativeRate", new JsonPrimitive(true));
        config.set("gun.fireRateMultiplier", new JsonPrimitive(2));
        check(FireTiming.plan(FireTiming.Settings.read(config), 12, true, true, 0, 1).ticks() == 6, "automatic cadence applies the rate multiplier");
        config.set("gun.randomInterval", new JsonPrimitive(true));
        check(FireTiming.plan(FireTiming.Settings.read(config), 12, true, true, 0, 8).ticks() == 4, "randomized intervals also apply the rate multiplier");

        config.set("gun.firingMode", new JsonPrimitive("BURST"));
        FireTiming.Settings burst = FireTiming.Settings.read(config);
        int remaining = 0, elapsed = 0;
        int[] expectedTimes = {1, 2, 8, 9, 10, 16};
        for (int expected : expectedTimes) {
            FireTiming.Plan plan = FireTiming.plan(burst, 12, true, true, remaining, 8);
            elapsed += plan.ticks(); remaining = plan.burstRemaining();
            check(elapsed == expected, "burst count, within-burst rate and inter-burst rate use one cadence");
        }
        check(remaining == 0, "two complete bursts finish with no queued native burst");
        config.set("gun.recovery", new JsonPrimitive(10));
        check(FireTiming.plan(FireTiming.Settings.read(config), 12, true, true, 0, 8).ticks() == 10, "explicit recovery is a minimum action duration");
        check(FireTiming.plan(burst, 12, true, false, 0, 8).ticks() == 12, "a native adapter without override support keeps its native floor");
        check(FireTiming.plan(burst, 12, false, false, 0, 8).burstRemaining() == 0, "non-gun native actions do not invent a burst mechanic");
        check(FireTiming.scaledTicks(1200, 0.05) == 24000 && FireTiming.scaledTicks(1, 20) == 1, "configured cadence stays within bounded server ticks");
        check(FireTiming.scaledTicks(4, Double.NaN) == 4, "invalid external rate data cannot poison the firing clock");
        check(FireTiming.reschedule(15, 10, 6) == 16, "changing rate shortens an old outstanding wait from the last shot");
        check(FireTiming.reschedule(15, 10, 20) == 30, "slower rate edits do not append a second full wait");
        check(FireTiming.reschedule(15, 10, 1) == 15, "an elapsed new interval is immediately ready");
        check(FireTiming.reschedule(15, -1000, 6) == 15 && FireTiming.reschedule(15, 20, 6) == 15, "unset and rolled-back shot times recover safely");
        check(FireTiming.remainingMillis(16, 15) == 50 && FireTiming.remainingMillis(16, 16) == 0, "native clock has no additional boundary tick");
        FireTiming.Settings beforeAmmoEdit = FireTiming.Settings.read(config);
        config.set("ammo.defaultReserve", new JsonPrimitive(50));
        check(beforeAmmoEdit.equals(FireTiming.Settings.read(config)), "unrelated ammunition edits retain the firing plan");
    }
    private static void graphs() {
        Map<String, ActionRegistry.Definition> actions = Map.of("slashblade:tests/basic", definition("slashblade:tests/basic", "NORMAL_ATTACK"));
        ComboGraph compiled = GraphValidator.validate(graph(), actions, false);
        check(compiled.nodes.size() == 3 && compiled.nodes.get("start").onFailure.equals("far"), "conditional graph retains separate edges");
        JsonObject cycle = graph(); cycle.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("onSuccess", "start");
        rejected(() -> GraphValidator.validate(cycle, actions, false), "cycle rejection");
        JsonObject dangling = graph(); dangling.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("onTimeout", "absent");
        rejected(() -> GraphValidator.validate(dangling, actions, false), "dangling timeout edge");
        JsonObject duplicate = graph(); duplicate.getAsJsonArray("nodes").add(duplicate.getAsJsonArray("nodes").get(0).deepCopy());
        rejected(() -> GraphValidator.validate(duplicate, actions, false), "duplicate node");
        JsonObject conditions = graph(); conditions.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("conditions", "always");
        rejected(() -> GraphValidator.validate(conditions, actions, false), "condition arrays are strict");
        JsonObject timeout = graph(); timeout.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("timeout", 1.5);
        rejected(() -> GraphValidator.validate(timeout, actions, false), "timeout is an integer");
        JsonObject params = graph(); params.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("parameters").addProperty("damage", 999);
        rejected(() -> GraphValidator.validate(params, actions, false), "unknown action parameters rejected");
        rejected(() -> GraphValidator.validate(graph(), Map.of(), false), "new graphs require installed actions");
        check(GraphValidator.validate(graph(), Map.of(), true).nodes.size() == 3, "stored missing actions remain recoverable");
        rejected(() -> Conditions.validate(new JsonPrimitive("execute_arbitrary_code")), "condition whitelist");
        rejected(() -> Conditions.validate(SafeJson.parseObject("{\"id\":\"distance\",\"min\":6,\"max\":2}")), "condition range order");
        ActionRegistry.Definition def = actions.values().iterator().next();
        JsonObject invalid = new JsonObject(); invalid.addProperty("resourceMultiplier", -1);
        rejected(() -> ActionConfig.validate(invalid, def), "resource multipliers bounded");
        check(ActionConfig.validate(new JsonObject(), def).getAsJsonObject("parameters").get("count").getAsInt() == 1, "native parameter defaults");
    }
    private static void scheduling() {
        Scheduler scheduler = new Scheduler();
        check(scheduler.acquire("RELOAD", "reload", 0, 20, false, false).ok, "reload owns main channel");
        check(!scheduler.acquire("SWITCH", "slot:1", 1, 4, false, false).ok, "switch cannot overwrite reload");
        check(!scheduler.acquire("BLADE", "phantom", 1, 4, true, true).ok, "phantom cannot overlap gun reload");
        scheduler.clear(); check(scheduler.acquire("BLADE", "normal", 0, 5, true, false).ok, "blade owns main channel");
        for (int i = 0; i < 4; i++) check(scheduler.acquire("BLADE", "phantom" + i, 0, 10, true, true).ok, "bounded auxiliary actions");
        check(!scheduler.acquire("BLADE", "extra", 0, 10, true, true).ok, "auxiliary limit");
        check(!scheduler.acquire("BLADE", "phantom0", 0, 10, true, true).ok, "duplicate auxiliary action");
        scheduler.expire(6); check(!scheduler.busy(6), "main channel expires");
        check(!scheduler.acquire("GUN", "fire", 6, 1, true, false).ok, "remaining blade auxiliary actions exclude gun channel");
        scheduler.expire(11); check(scheduler.acquire("SWITCH", "slot:2", 11, 4, false, false).ok, "switch available after native channels finish");
        scheduler.clear(); check(!scheduler.busy(11), "lifecycle reset clears scheduler");
    }
    private static CombatDecisions.Snapshot combatSnapshot(int active, double distance, boolean requireLoaded, List<CombatDecisions.Weapon> weapons) {
        return new CombatDecisions.Snapshot(new UUID(0, 1), new UUID(0, 2), "minecraft:overworld", 100, 3, 5,
            active, "tests:active", distance, 0.75, 0.25, 1, 4, 0.35, false, false, false, requireLoaded, weapons);
    }
    private static void combatDecisions() {
        var empty = new CombatDecisions.Weapon(0, "tests:empty", false, true, 0, 30, 1000);
        var backup = new CombatDecisions.Weapon(1, "tests:backup", false, true, 30, 30, 500);
        check(CombatDecisions.calculate(combatSnapshot(0, 24, false, List.of(empty, backup))).slot() == 1,
            "loaded backup outweighs an empty high-priority gun");
        var emptyBackup = new CombatDecisions.Weapon(1, "tests:emptyBackup", false, true, 0, 30, 500);
        check(CombatDecisions.calculate(combatSnapshot(0, 24, true, List.of(empty, emptyBackup))).slot() == -1,
            "all empty magazines leave tactical switching unavailable so reload can proceed");
        var primary = new CombatDecisions.Weapon(0, "tests:primary", false, true, 20, 30, 800);
        var sidearm = new CombatDecisions.Weapon(2, "tests:sidearm", false, true, 20, 30, 800);
        var melee = new CombatDecisions.Weapon(4, "tests:blade", true, false, -1, -1, 500);
        var lastWeapon = new CombatDecisions.Weapon(4, "tests:lastWeapon", true, false, -1, -1, 0);
        for (double distance : new double[]{1, 12, 64}) {
            var exhausted = combatSnapshot(0, distance, true, List.of(empty, emptyBackup, lastWeapon));
            check(CombatDecisions.meleeFallback(exhausted.weapons(), exhausted.activeSlot()) == 4,
                "complete ammo exhaustion equips the last melee weapon at any target distance, regardless of gun priority");
        }
        var preferredBlade = new CombatDecisions.Weapon(5, "tests:preferredBlade", true, false, -1, -1, 700);
        check(CombatDecisions.meleeFallback(List.of(empty, melee, preferredBlade), 0) == 5, "exhaustion compares configured priority only among melee weapons");
        var equalBlade = new CombatDecisions.Weapon(5, "tests:equalBlade", true, false, -1, -1, 500);
        check(CombatDecisions.meleeFallback(List.of(melee, equalBlade), 5) == 5, "equal melee priorities retain the equipped weapon without repeated switching");
        check(CombatDecisions.meleeFallback(List.of(empty, emptyBackup), 0) == -1, "no melee equipment does not select an exhausted gun as a fallback");
        check(CombatDecisions.calculate(combatSnapshot(0, 3, false, List.of(primary, sidearm, melee))).slot() == 4,
            "a reachable melee weapon wins at close finishing distance");
        check(CombatDecisions.calculate(combatSnapshot(0, 10, false, List.of(primary, sidearm))).slot() == 2,
            "sidearm range can outweigh the current primary slot");
        var equivalent = new CombatDecisions.Weapon(1, "tests:equivalent", false, true, 20, 30, 800);
        check(CombatDecisions.calculate(combatSnapshot(1, 24, false, List.of(primary, equivalent))).slot() == 1,
            "equal guns retain the current slot without oscillation");
        List<CombatDecisions.Weapon> mutable = new ArrayList<>(List.of(primary, equivalent));
        var snapshot = combatSnapshot(1, 24, false, mutable); mutable.clear();
        check(snapshot.weapons().size() == 2 && CombatDecisions.calculate(snapshot).slot() == 1,
            "async snapshot keeps its own immutable candidate list");
        UUID npc = new UUID(0, 1), target = new UUID(0, 2), other = new UUID(0, 3);
        check(snapshot.matches(npc, target, "minecraft:overworld", 108, 3, 5, 1, "tests:active"), "advice accepts the maximum valid age");
        check(!snapshot.matches(other, target, "minecraft:overworld", 101, 3, 5, 1, "tests:active"), "advice rejects another NPC");
        check(!snapshot.matches(npc, other, "minecraft:overworld", 101, 3, 5, 1, "tests:active"), "advice rejects a changed target");
        check(!snapshot.matches(npc, target, "minecraft:the_nether", 101, 3, 5, 1, "tests:active"), "advice rejects another dimension");
        check(!snapshot.matches(npc, target, "minecraft:overworld", 101, 4, 5, 1, "tests:active"), "advice rejects a changed config revision");
        check(!snapshot.matches(npc, target, "minecraft:overworld", 101, 3, 6, 1, "tests:active"), "advice rejects a replaced weapon instance");
        check(!snapshot.matches(npc, target, "minecraft:overworld", 101, 3, 5, 0, "tests:active"), "advice rejects a changed active slot");
        check(!snapshot.matches(npc, target, "minecraft:overworld", 101, 3, 5, 1, "tests:changed"), "advice rejects a changed weapon ID");
        check(!snapshot.matches(npc, target, "minecraft:overworld", 109, 3, 5, 1, "tests:active"), "advice rejects expired snapshots");
        check(!snapshot.matches(npc, target, "minecraft:overworld", 99, 3, 5, 1, "tests:active"), "advice rejects snapshots from a later tick");
        rejected(() -> combatSnapshot(0, 20, false, List.of(primary, primary)), "duplicate candidate slots rejected");
        rejected(() -> combatSnapshot(0, Double.NaN, false, List.of(primary)), "non-finite decision geometry rejected");
        rejected(() -> new CombatDecisions.Weapon(6, "tests:invalid", false, true, 1, 30, 1), "candidate slot bound");
        rejected(() -> new CombatDecisions.Weapon(0, "", false, true, 1, 30, 1), "candidate identity required");
        rejected(() -> new CombatDecisions.Weapon(0, "tests:invalid", true, true, 1, 30, 1), "candidate domain must be unambiguous");
    }
    private static Ballistics.Request throwRequest(Ballistics.Trajectory mode, Ballistics.Vec target, Ballistics.Vec movement, int lead) {
        return new Ballistics.Request(new Ballistics.Vec(0, 1.6, 0), target, movement,
            new Ballistics.Physics(1.5, 0.03, 0.99, 0.15, 4, 100), mode, 80, 70, 64, 0.25, 0.3, lead, true);
    }
    private static void ballistics() {
        var grounded = Ballistics.targetMovement(new Ballistics.Vec(0.2, -0.08, 0), true);
        check(grounded.y() == 0 && grounded.x() == 0.2, "grounded gravity does not predict an underground landing point");
        check(Ballistics.targetMovement(new Ballistics.Vec(0, -0.08, 0), false).y() == -0.08, "airborne target fall remains part of prediction");
        check(Ballistics.targetMovement(new Ballistics.Vec(100, 0, 0), true).length() <= 2, "target movement snapshot remains bounded");
        var still = new Ballistics.Vec(0, 0, 0); var goal = new Ballistics.Vec(20, 1, 0);
        var request = throwRequest(Ballistics.Trajectory.AUTO, goal, still, 0);
        var candidates = Ballistics.candidates(request);
        check(!candidates.isEmpty() && candidates.stream().noneMatch(c -> c.mode() == Ballistics.Trajectory.TRACKING), "AUTO only considers straight and curved flight");
        for (var candidate : candidates) {
            check(Math.abs(candidate.velocity().length() - 1.5) < 1E-5, "ballistic solve preserves launch speed");
            check(candidate.points().get(candidate.points().size() - 1).distance(candidate.goal()) < 1E-6, "discrete drag and gravity arrive at the intended point");
        }
        check(Ballistics.choose(new Ballistics.World(request, candidates, List.of(), List.of())).mode() == Ballistics.Trajectory.STRAIGHT, "an unobstructed shot prefers the direct trajectory");
        var wall = new Ballistics.Bounds(9, 0, -2, 10, 2.5, 2);
        var clearArc = Ballistics.choose(new Ballistics.World(request, candidates, List.of(wall), List.of()));
        check(clearArc != null && clearArc.mode() == Ballistics.Trajectory.CURVE, "a high arc reaches a target behind a low wall");
        var ceiling = new Ballistics.Bounds(-1, 2, -3, 21, 3, 3);
        check(Ballistics.choose(new Ballistics.World(request, candidates, List.of(wall, ceiling), List.of())) == null, "wall and ceiling collisions reject an unsafe throw");
        check(Ballistics.choose(new Ballistics.World(request, candidates, List.of(), List.of(new Ballistics.Bounds(19, 0, -1, 21, 2, 1)))) == null, "a landing near self or allies is rejected");
        var moving = throwRequest(Ballistics.Trajectory.AUTO, goal, new Ballistics.Vec(0.1, 0, 0), 30);
        var predicted = Ballistics.candidates(moving);
        check(!predicted.isEmpty() && predicted.stream().allMatch(c -> c.goal().x() > 23), "target prediction includes windup and flight time");
        check(Ballistics.candidates(throwRequest(Ballistics.Trajectory.AUTO, new Ballistics.Vec(200, 1, 0), still, 0)).isEmpty(), "calculation range bounds distant work");
        var slow = new Ballistics.Request(new Ballistics.Vec(0, 1, 0), new Ballistics.Vec(0.5, 1, 0), still,
            new Ballistics.Physics(0.015, 0.03, 0.99, 0.1, 0, 100), Ballistics.Trajectory.STRAIGHT, 80, 70, 64, 0, 0.1, 0, false);
        var slowCandidates = Ballistics.candidates(slow);
        check(!slowCandidates.isEmpty() && Math.abs(slowCandidates.get(0).velocity().length() - 0.015) < 1E-6, "a 0.01 multiplier remains a slow native launch");
        check(ConfigSchema.get("throw.slot0.speed").validate(new JsonPrimitive(0.01)).getAsDouble() == 0.01, "editor accepts the required 0.01 speed multiplier");
        rejected(() -> new Ballistics.Vec(Double.NaN, 0, 0), "non-finite projectile geometry is rejected");
        rejected(() -> Ballistics.path(still, new Ballistics.Vec(1, 0, 0), 0.03, 0.99, 601), "trajectory time bounds work");
        var cells = Ballistics.cells(new Ballistics.Vec(0.99, 0.5, 0.99), new Ballistics.Vec(1.01, 0.5, 1.01), 0, 16);
        check(cells.contains(new Ballistics.Cell(1, 0, 0)) && cells.contains(new Ballistics.Cell(0, 0, 1)), "swept sampling includes corner-crossing cells");
        boolean[] blocked = new boolean[9 * 9 * 9];
        for (int y = 0; y < 9; y++) for (int z = 0; z < 9; z++) blocked[(y * 9 + z) * 9 + 4] = !(y == 5 && z == 4);
        var grid = new Ballistics.Grid(still, new Ballistics.Vec(2.5, 4.5, 4.5), new Ballistics.Vec(6.5, 4.5, 4.5), blocked);
        Arrays.fill(blocked, true); var route = Ballistics.route(grid);
        check(!route.isEmpty() && route.stream().anyMatch(p -> Math.floor(p.x()) == 4 && Math.floor(p.y()) == 5 && Math.floor(p.z()) == 4), "immutable 3D routing finds the opening without cutting through the wall");
        check(Ballistics.route(new Ballistics.Grid(still, new Ballistics.Vec(2.5, 4.5, 4.5), goal, blocked)).isEmpty(), "an unreachable tracking grid fails within its node budget");
        var savedWalls = new ArrayList<Ballistics.Bounds>(); savedWalls.add(wall);
        var snapshot = new Ballistics.World(request, candidates, savedWalls, List.of()); savedWalls.clear();
        check(snapshot.walls().size() == 1, "worker collision snapshots have no mutable caller aliases");
        var repaired = new NpcConfig(SafeJson.parseObject("{\"values\":{\"throw.slot0.minRange\":40,\"throw.slot0.maxRange\":8}}"));
        check(repaired.number("throw.slot0.minRange") <= repaired.number("throw.slot0.maxRange"), "stored throwing ranges recover safely");
    }
    private static void throwingPolicy() {
        check(ThrowPolicy.requiresSafeDistance("SAFE"), "safe-distance timing retains blast clearance checks");
        for (String timing : List.of("SHOOT", "RELOAD", "RANDOM"))
            check(!ThrowPolicy.requiresSafeDistance(timing), "other throw timings do not impose hidden blast clearance: " + timing);
        check(ThrowPolicy.shootingTask(160, 100, 105, true, false), "slow automatic guns remain a shooting task beyond the recent-shot window");
        check(ThrowPolicy.shootingTask(160, 100, -1000, true, false), "automatic aiming and charging can trigger a throw before a bullet is released");
        check(ThrowPolicy.shootingTask(160, 100, -1000, false, true), "a native firing action can trigger a throw without reporting a released bullet");
        check(ThrowPolicy.shootingTask(160, 100, 155, false, false), "a recent manual shot retains its throw opportunity");
        check(!ThrowPolicy.shootingTask(160, 100, 105, false, false), "an old manual shot does not create a persistent shooting task");
        check(!ThrowPolicy.shootingTask(160, 150, 145, false, false), "a shot at the previous target does not activate the new target's manual throw timing");
        check(!ThrowPolicy.shootingTask(160, 100, 170, false, false), "a future shot timestamp does not activate manual throw timing");
        check(!ThrowPolicy.shootingTask(90, 100, 105, true, true), "a rolled-back target clock cannot activate a shooting task");
        check(ThrowPolicy.predictionFresh(123, 100, 122, true), "a 22-tick budgeted terrain capture remains usable at release");
        check(ThrowPolicy.predictionFresh(128, 100, 128, true), "planning may complete at the last allowed tick");
        check(!ThrowPolicy.predictionFresh(129, 100, 129, true), "a late completed plan cannot extend the planning timeout");
        check(!ThrowPolicy.predictionFresh(124, 100, 113, true), "an old collision capture must be refreshed before release");
        check(!ThrowPolicy.predictionFresh(110, 100, -1, true) && !ThrowPolicy.predictionFresh(110, 100, 111, true),
            "missing and future collision captures cannot authorize a release");
        check(ThrowPolicy.predictionFresh(124, 100, 113, false) && !ThrowPolicy.predictionFresh(99, 100, 100, false),
            "initial action acquisition has the planning budget and rejects a reversed clock");
    }
    private static void ammunitionPolicy() {
        check(!ReloadPolicy.hasLoadedShot(true, 1, 2, false) && ReloadPolicy.hasLoadedShot(true, 2, 2, false),
            "multi-round triggers reload when the remaining rounds cannot pay for one shot");
        check(ReloadPolicy.hasLoadedShot(true, 0, 2, true) && ReloadPolicy.hasLoadedShot(false, null, 2, false),
            "infinite and native inventory-ammo guns are not treated as empty magazines");
        check(ReloadPolicy.requested("EMPTY", true, false, 1, 0), "empty-shot detection includes a partial multi-round charge");
        check(ReloadPolicy.requested("THRESHOLD", true, true, 3, 3), "configured tactical reload starts at its threshold during combat");
        check(!ReloadPolicy.requested("THRESHOLD", true, true, 4, 3) && !ReloadPolicy.requested("EMPTY", true, true, 3, 3),
            "other reload timing does not inherit the threshold rule");
        check(ReloadPolicy.requested("IDLE", false, true, 3, 0) && !ReloadPolicy.requested("IDLE", true, true, 3, 0),
            "idle reload is limited to the configured idle condition");
        check(ReloadPolicy.bufferNeed(30, 12, 300) == 18 && ReloadPolicy.bufferNeed(30, 30, 300) == 0,
            "native buffered ammunition is credited once across reload retries");
        check(ReloadPolicy.bufferNeed(30, 29, 29) == 0 && ReloadPolicy.bufferNeed(Integer.MAX_VALUE, 1, 100000) == 99999,
            "buffer replenishment cannot overfill capacity or overflow arithmetic");
        check(AmmoPolicy.allowsReserve("INDEPENDENT") && !AmmoPolicy.allowsItems("INDEPENDENT", false),
            "reserve-only selection never consumes NPC inventory items");
        check(!AmmoPolicy.allowsReserve("NPC_ITEMS") && AmmoPolicy.allowsItems("NPC_ITEMS", false),
            "inventory-only selection never consumes the independent reserve");
        check(AmmoPolicy.allowsReserve("MIXED") && AmmoPolicy.allowsItems("MIXED", false), "mixed selection exposes both sources");
        check(!AmmoPolicy.allowsItems("MIXED", true) && !AmmoPolicy.allowsItems("NPC_ITEMS", true),
            "infinite ammunition does not consume real inventory items");
        check(AmmoPolicy.reserveFirst("RESERVE_FIRST") && !AmmoPolicy.reserveFirst("ITEMS_FIRST"),
            "both source-priority choices retain their configured order");
    }
    private static void reloadTiming() {
        check(ReloadTiming.steps(7, 2) == 4 && ReloadTiming.stepAt(100, 31, 4, 4) == 131, "the last partial ammo step ends at the requested reload duration");
        check(ReloadTiming.stepAt(100, 1, 1, 4) == 101 && ReloadTiming.stepAt(100, 1, 4, 4) == 101, "short reloads may combine native ammo steps within one server tick");
        rejected(() -> ReloadTiming.speed(30, 0), "zero reload duration rejected");
        ReloadTiming.Clock clock = new ReloadTiming.Clock(20, 1000);
        long timestamp = clock.adjust(1000, 21, 1050, 2);
        check(1050 - timestamp == 100, "reload clock advances by server ticks at custom speed");
        timestamp = clock.adjust(timestamp, 21, 1090, 2);
        check(1090 - timestamp == 100, "duplicate reads in the same tick do not advance the reload clock");
        timestamp = clock.adjust(timestamp, 22, 1210, 2);
        check(1210 - timestamp == 200, "forward wall-clock changes do not skip native reload stages");
        timestamp = clock.adjust(timestamp, 23, 1160, 2);
        check(1160 - timestamp == 300, "backward wall-clock changes do not reverse reload progress");
        timestamp = clock.adjust(1160, 24, 1205, 2);
        check(1205 - timestamp == 100, "native script timestamp resets begin a new reload stage");
        ReloadTiming.Clock fractional = new ReloadTiming.Clock(0, 1000); timestamp = 1000;
        for (int tick = 1; tick <= 4; tick++) timestamp = fractional.adjust(timestamp, tick, 1000 + tick * 50, 0.005);
        check(1200 - timestamp == 1, "fractional reload time accumulates without losing sub-millisecond progress");
    }
    private static void bowDraw() {
        UUID target = new UUID(7, 1), other = new UUID(7, 2);
        BocekDrawState draw = new BocekDrawState(100, target, 3, 5);
        boolean waiting = true;
        for (int tick = 100; tick < 100 + BocekDrawState.FULL_DRAW_TICKS; tick++) {
            // Adapter tick plus repeated fire retries in the same tick must keep the same draw, without speeding it up.
            for (int retry = 0; retry < 8; retry++) waiting &= draw.advance(tick, target, 3, 5, true) && !draw.release(tick);
        }
        check(waiting, "BOCEK retries preserve continuous draw progress and never release before full charge");
        long releaseAt = 100 + BocekDrawState.FULL_DRAW_TICKS;
        check(draw.advance(releaseAt, target, 3, 5, true) && draw.release(releaseAt) && !draw.release(releaseAt),
            "a fully charged BOCEK release is claimed once, including reentrant fire requests");
        draw.cancel();
        check(!draw.releasing() && !draw.release(releaseAt), "cancel invalidates even an already claimed release");

        BocekDrawState paused = new BocekDrawState(100, target, 3, 5);
        boolean preserved = true;
        for (int tick = 101; tick <= 106; tick++) preserved &= paused.advance(tick, target, 3, 5, true);
        for (int tick = 107; tick <= 136; tick++) preserved &= paused.pause(tick, target, 3, 5, true) && !paused.release(tick);
        for (int tick = 137; tick <= 143; tick++) preserved &= paused.advance(tick, target, 3, 5, true) && !paused.release(tick);
        check(preserved, "throw planning pauses a partial bow draw without charging or cancelling it");
        check(paused.advance(144, target, 3, 5, true) && paused.release(144), "failed planning resumes the saved bow draw at its remaining duration");
        BocekDrawState invalidPause = new BocekDrawState(0, target, 3, 5);
        check(!invalidPause.pause(1, other, 3, 5, true) && !invalidPause.advance(1, target, 3, 5, true),
            "paused charging still rejects changed targets and cannot revive cancelled state");

        BocekDrawState changedTarget = new BocekDrawState(0, target, 3, 5);
        check(!changedTarget.advance(1, other, 3, 5, true) && !changedTarget.advance(1, target, 3, 5, true),
            "switching targets and switching back cannot reuse the old bow draw");
        BocekDrawState changedConfig = new BocekDrawState(0, target, 3, 5);
        check(!changedConfig.advance(1, target, 4, 5, true), "a configuration change cancels an unreleased bow draw");
        BocekDrawState changedWeapon = new BocekDrawState(0, target, 3, 5);
        check(!changedWeapon.advance(1, target, 3, 6, true), "a weapon instance change cancels an unreleased bow draw");
        BocekDrawState interrupted = new BocekDrawState(0, target, 3, 5);
        check(!interrupted.advance(1, target, 3, 5, false) && !interrupted.advance(1, target, 3, 5, true),
            "reload, throw, stop or invalid combat conditions require a fresh bow draw");
        BocekDrawState gap = new BocekDrawState(0, target, 3, 5);
        check(!gap.advance(2, target, 3, 5, true), "a skipped NPC tick cannot credit unattended bow charge");
        BocekDrawState backwards = new BocekDrawState(100, target, 3, 5);
        check(!backwards.advance(99, target, 3, 5, true), "backward game time cannot retain a bow release");
    }
    private static void presets() {
        ActionRegistry.INSTANCE.clear();
        ActionRegistry.INSTANCE.register(definition("slashblade:tests/sa/one", "SPECIAL_ATTACK"));
        ActionRegistry.INSTANCE.register(definition("slashblade:tests/sa/two", "SPECIAL_ATTACK"));
        ActionRegistry.INSTANCE.register(definition("slashblade:tests/phantom/basic", "PHANTOM_SWORD", "basic"));
        check(Presets.template("BEGINNER").getAsJsonObject("actions").getAsJsonObject("slashblade:tests/phantom/basic").get("enabled").getAsBoolean(), "beginner includes basic phantom action");
        JsonObject normal = Presets.template("NORMAL");
        check(normal.getAsJsonObject("actions").getAsJsonObject("slashblade:tests/sa/one").get("enabled").getAsBoolean(), "first discovered SA enabled");
        check(normal.getAsJsonObject("actions").getAsJsonObject("slashblade:tests/sa/two").get("enabled").getAsBoolean(), "multiple discovered SAs enabled independently");
        JsonObject maid = Presets.template("MAID");
        check(maid.getAsJsonObject("values").get("slashblade.attackInterval").getAsInt() == 4, "maid decision cadence");
        check(!maid.getAsJsonObject("actions").getAsJsonObject("slashblade:tests/sa/one").get("linkPhantom").getAsBoolean(), "maid phantoms use an independent lane");
        check(Presets.template("CUSTOM").getAsJsonObject("actions").getAsJsonObject("slashblade:tests/sa/one").get("enabled").getAsBoolean() == false, "custom preset avoids automatic action assumptions");
        ActionRegistry.INSTANCE.clear();
    }
    private static void api() throws Exception {
        check(Result.success().ok && Result.success().code.equals("SUCCESS"), "success contract");
        check(!Result.fail("UNSUPPORTED").ok && Result.fail("UNSUPPORTED").data == null, "failure contract");
        String[][] methods = {
            {"NpcProxy", "getCurrentWeapon", "getCapabilities", "switchWeapon", "getGun", "getSlashBlade", "getCombat", "getAmmo"},
            {"GunProxy", "fire", "stopFire", "reload", "getAmmoState", "setFiringMode", "getRecoil"},
            {"BladeProxy", "getAvailableActions", "executeAction", "useSpecialAttack", "setCombo", "startCombo", "stopCombo", "setPreset"},
            {"AmmoProxy", "getState", "addReserveAmmo", "setReserveAmmo", "setNoAmmoBehavior"},
            {"CombatProxy", "getTarget", "requestRetreat", "requestHoldPosition", "requestAttack"}
        };
        for (String[] contract : methods) {
            Set<String> present = new HashSet<>();
            for (var method : Class.forName("bincnpc.gun.common.script.ScriptApi$" + contract[0]).getDeclaredMethods()) present.add(method.getName());
            for (int i = 1; i < contract.length; i++) check(present.contains(contract[i]), "missing script method: " + contract[0] + "." + contract[i]);
        }
    }
}
