package bincnpc.gun.common.api;

/** Stable ES5 result: acceptance never promises a hit. */
public final class Result {
    public final boolean ok;
    public final String code;
    public final String message;
    public final Object data;

    private Result(boolean ok, String code, String message, Object data) {
        this.ok = ok;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static Result success() { return success(null); }
    public static Result success(Object data) { return new Result(true, "SUCCESS", "", data); }
    public static Result fail(String code) { return fail(code, "cnpcgun.error." + code.toLowerCase(java.util.Locale.ROOT)); }
    public static Result fail(String code, String message) { return new Result(false, code, message, null); }
}
