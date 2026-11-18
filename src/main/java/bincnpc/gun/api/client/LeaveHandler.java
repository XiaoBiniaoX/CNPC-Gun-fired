package bincnpc.gun.api.client;

/** Optional Screen hook for normal page departure. Not called for forced removed() cleanup. */
public interface LeaveHandler {
    /** Called on the client thread; continue immediately, after save acknowledgement, or not at all to cancel.
     * The host dispatches the continuation to the client thread, once, without requesting leave again. */
    void requestLeave(Runnable continueLeaving);
}
