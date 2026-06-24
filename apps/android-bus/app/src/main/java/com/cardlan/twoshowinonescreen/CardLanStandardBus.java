package com.cardlan.twoshowinonescreen;

/**
 * Minimal JNI bridge for the CardLan 618K SDK.
 *
 * The class and native method names must match the vendor library's exported
 * JNI symbols.
 */
public final class CardLanStandardBus {
    static {
        System.loadLibrary("cardlan_StandardBus663");
    }

    public int callInitDev() {
        return InitDev();
    }

    public int callCardReset(byte[] cardSn) {
        if (cardSn == null || cardSn.length < 16) {
            return -2;
        }
        return CardReset(cardSn, 0);
    }

    private static native int InitDev();

    private static native int CardReset(byte[] cardSn, int type);
}
