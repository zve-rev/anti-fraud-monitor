package com.zverev.util;

public final class CardMasker {

    private CardMasker() {}

    public static String mask(String cardToken) {
        if (cardToken == null || cardToken.length() < 4) {
            return "****";
        }
        return "**** " + cardToken.substring(cardToken.length() - 4);
    }
}
