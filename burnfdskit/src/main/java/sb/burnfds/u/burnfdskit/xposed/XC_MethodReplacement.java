package sb.burnfds.u.burnfdskit.xposed;

public abstract class XC_MethodReplacement extends XC_MethodHook {

    protected abstract Object replaceHookedMethod(MethodHookParam param) throws Throwable;

    @Override
    protected final void beforeHookedMethod(MethodHookParam param) {
        try {
            param.setResult(replaceHookedMethod(param));
        } catch (Throwable t) {
            param.setThrowable(t);
        }
    }

    public static XC_MethodReplacement returnConstant(final Object result) {
        return new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                return result;
            }
        };
    }
}
