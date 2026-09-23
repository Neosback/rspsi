package com.rspsi.cache.definition;

/**
 * Player variable state used to pick a multiloc's visible transform.
 *
 * <p>The client reads a varbit (or varp) when it transforms a placed loc
 * ({@code runescape-client/ObjectComposition.transform()}). An editor has no
 * logged-in player, so it resolves against an explicit state: a fresh account
 * by default, and later a simulation's live values.</p>
 */
public interface ObjectVarState {
    int varbitValue(int varbitId);

    int varpValue(int varpId);

    /** Every var at its initial value, 0 - what a new account sees. */
    static ObjectVarState freshAccount() {
        return FreshAccount.INSTANCE;
    }

    enum FreshAccount implements ObjectVarState {
        INSTANCE;

        @Override
        public int varbitValue(int varbitId) {
            return 0;
        }

        @Override
        public int varpValue(int varpId) {
            return 0;
        }
    }
}
