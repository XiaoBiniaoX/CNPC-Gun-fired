package bincnpc.gun.client.animation;

/** Early vanilla model hooks stay inert until the late client animation handler is installed. */
public final class BipedAnimationHooks {
    public interface Handler {
        void before(Object model);
        void after(Object model, Object entity, float partialTick);
    }
    private static Handler handler;
    private BipedAnimationHooks() {}
    public static void bind(Handler value) { handler = value; }
    public static void before(Object model) {
        if (handler != null) handler.before(model);
    }
    public static void after(Object model, Object entity, float partialTick) {
        if (handler != null && model != null && entity != null) handler.after(model, entity, partialTick);
    }
}
