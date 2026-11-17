package bincnpc.gun.compat.tacz;

import bincnpc.gun.CNPCGun;
import java.lang.reflect.Method;
import java.util.function.LongSupplier;

/** TaCZ 1.2 uses its animation clock; older releases use system time. No optional class is initialized here. */
public final class TaczTime {
    private static final LongSupplier CLOCK = findClock();
    private static boolean reported;
    private TaczTime() {}
    public static long nowMillis() { return CLOCK.getAsLong(); }
    private static LongSupplier findClock() {
        try {
            Method method = Class.forName("com.tacz.guns.util.time.GunTime", false, TaczTime.class.getClassLoader()).getMethod("nowMillis");
            if (method.getReturnType() != long.class) throw new NoSuchMethodException("GunTime.nowMillis return type");
            return () -> {
                try { return (long)method.invoke(null); }
                catch (ReflectiveOperationException | RuntimeException | LinkageError error) { report(error); return -1; }
            };
        } catch (ClassNotFoundException legacy) {
            return System::currentTimeMillis;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            report(error); return () -> -1;
        }
    }
    private static void report(Throwable error) {
        if (!reported) { reported = true; CNPCGun.LOGGER.warn("TaCZ native clock unavailable; native reload timing retained", error); }
    }
}
