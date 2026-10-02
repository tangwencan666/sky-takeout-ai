package com.tang.sky.common;

/** 用户身份只来自已校验JWT；线程复用前必须remove，不能让下一个请求继承旧身份。 */
public final class UserContext {
    public record Identity(long id, String role) {}
    private static final ThreadLocal<Identity> CURRENT = new ThreadLocal<>();
    private UserContext() {}
    public static void set(Identity identity) { CURRENT.set(identity); }
    public static Identity require() {
        Identity identity = CURRENT.get();
        if (identity == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return identity;
    }
    public static long userId() { return require().id(); }
    public static void clear() { CURRENT.remove(); }
}
