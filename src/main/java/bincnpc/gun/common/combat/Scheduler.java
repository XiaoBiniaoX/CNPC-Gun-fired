package bincnpc.gun.common.combat;

import bincnpc.gun.common.api.Result;
import java.util.*;

public final class Scheduler {
    public String channel = "", action = "";
    public long until;
    public boolean interruptible = true;
    private final Map<String, Long> auxiliary = new HashMap<>();

    public boolean busy(long now) { return !channel.isEmpty() && now < until; }
    public Result acquire(String channel, String action, long now, int duration, boolean interruptible, boolean parallel) {
        expire(now);
        if (parallel) {
            if (busy(now) && !this.channel.equals("BLADE")) return Result.fail("CONFLICT");
            if (auxiliary.containsKey(action)) return Result.fail("COOLDOWN");
            if (auxiliary.size() >= 4) return Result.fail("BUSY");
            auxiliary.put(action, now + Math.max(1, duration));
            return Result.success();
        }
        if (busy(now)) return Result.fail("BUSY");
        if (!channel.equals("BLADE") && !auxiliary.isEmpty()) return Result.fail("CONFLICT");
        this.channel = channel; this.action = action;
        this.until = now + Math.max(1, duration); this.interruptible = interruptible;
        return Result.success();
    }
    public void expire(long now) {
        auxiliary.values().removeIf(t -> t <= now);
        if (!channel.isEmpty() && now >= until) release();
    }
    public void release() { channel = ""; action = ""; until = 0; interruptible = true; }
    public void releaseAuxiliary(String action) { auxiliary.remove(action); }
    public void clear() { release(); auxiliary.clear(); }
}
