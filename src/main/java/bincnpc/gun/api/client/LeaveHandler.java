package bincnpc.gun.api.client;

/** Optional GuiScreen hook for normal departure, not forced onGuiClosed cleanup. */
public interface LeaveHandler {
    /** Continue immediately, after save acknowledgement, or not at all to cancel.
     * The host dispatches the continuation to the client thread once, without requesting leave again. */
    void requestLeave(Runnable continueLeaving);
}
