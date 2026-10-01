package io.github.ling.randombubble.core;

/** Validates the live owner chain; historical wrapper metadata cannot authorize a send. */
public final class ActiveConversationChain {
    private ActiveConversationChain() {}
    public static boolean consistent(Object fragment,Object contextFragment,Object managerGetterPie,Object managerFieldPie,
                                     Object pieGetterContext,Object pieFieldContext,Object fragmentCurrentContext,
                                     Object pieParam,Object contextParam) {
        return fragment!=null && contextFragment!=null && managerGetterPie!=null && managerFieldPie!=null
            && pieGetterContext!=null && pieFieldContext!=null && fragmentCurrentContext!=null
            && pieParam!=null && contextParam!=null
            && fragment==contextFragment && managerGetterPie==managerFieldPie
            && pieGetterContext==pieFieldContext && pieGetterContext==fragmentCurrentContext
            && pieParam==contextParam;
    }
    public static boolean sameCurrent(Object capturedPie,Object capturedContext,Object capturedParam,
                                      Object currentPie,Object currentContext,Object currentParam) {
        return capturedPie!=null && capturedContext!=null && capturedParam!=null
            && currentPie!=null && currentContext!=null && currentParam!=null
            && capturedPie==currentPie && capturedContext==currentContext && capturedParam==currentParam;
    }
}
