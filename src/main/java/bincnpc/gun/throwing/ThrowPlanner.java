package bincnpc.gun.throwing;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** The only accepted jobs capture immutable Ballistics records, never game objects or callbacks. */
public final class ThrowPlanner {
    private static final Object LIFECYCLE = new Object();
    private static volatile ThreadPoolExecutor executor;
    private static volatile long generation;
    public static final int MAX_QUEUE = 64;
    private ThrowPlanner() {}
    public static final class Ticket<T> implements Runnable {
        private final Supplier<T> calculation;
        private final long epoch;
        private volatile T result;
        private volatile boolean finished, cancelled;
        private Ticket(Supplier<T> calculation, long epoch) { this.calculation = calculation; this.epoch = epoch; }
        public boolean finished() { return finished || cancelled || epoch != generation; }
        public T result() { return finished && !cancelled && epoch == generation ? result : null; }
        public boolean current() { return !cancelled && epoch == generation; }
        public void cancel() { cancelled = true; result = null; ThreadPoolExecutor pool = executor; if (pool != null) pool.remove(this); }
        @Override public void run() {
            try {
                if (cancelled || epoch != generation || Thread.currentThread().isInterrupted()) return;
                T value = calculation.get();
                if (!cancelled && epoch == generation && !Thread.currentThread().isInterrupted()) result = value;
            } catch (RuntimeException invalid) { result = null; }
            finally { finished = true; }
        }
    }
    public static Ticket<List<Ballistics.Candidate>> plan(Ballistics.Request request) { return submit(() -> Ballistics.candidates(request)); }
    public static Ticket<Ballistics.Candidate> assess(Ballistics.World snapshot) { return submit(() -> Ballistics.choose(snapshot)); }
    public static Ticket<List<Ballistics.Vec>> route(Ballistics.Grid snapshot) { return submit(() -> Ballistics.route(snapshot)); }
    private static <T> Ticket<T> submit(Supplier<T> job) {
        synchronized (LIFECYCLE) {
            if (executor == null || executor.isShutdown()) return null;
            Ticket<T> ticket = new Ticket<>(job, generation);
            try { executor.execute(ticket); return ticket; } catch (RejectedExecutionException full) { return null; }
        }
    }
    public static void start() {
        synchronized (LIFECYCLE) {
            stop(); AtomicInteger names = new AtomicInteger();
            executor = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(MAX_QUEUE), task -> {
                Thread thread = new Thread(task, "cnpcgun-throw-" + names.incrementAndGet()); thread.setDaemon(true); thread.setPriority(Thread.NORM_PRIORITY - 1); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
            executor.allowCoreThreadTimeOut(true);
        }
    }
    public static void stop() {
        synchronized (LIFECYCLE) {
Object cnpcgun$value0; Ticket<?> cnpcgun$match0;

            generation++; ThreadPoolExecutor previous = executor; executor = null;
            if (previous != null) for (Runnable waiting : previous.shutdownNow()) if (((cnpcgun$value0 = waiting) instanceof Ticket<?> && (cnpcgun$match0 = (Ticket<?>)cnpcgun$value0) != null)) cnpcgun$match0.cancel();
        }
    }
}
