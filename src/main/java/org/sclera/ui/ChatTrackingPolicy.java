package org.sclera.ui;

/** Keeps gaze input out of chat and its virtual keyboard when the user disables chat tracking. */
public final class ChatTrackingPolicy {
    private ChatTrackingPolicy() {}

    public static boolean shouldForceHands(boolean trackingInChat, boolean chatScreenActive,
                                           boolean keyboardOverlayFocused) {
        return !trackingInChat && (chatScreenActive || keyboardOverlayFocused);
    }
}
