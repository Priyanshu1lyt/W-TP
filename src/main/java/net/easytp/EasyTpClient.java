package net.easytp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Easy TP - client-only entrypoint. Registers the keybind and watches for
 * server rejections of the /tp command so the GUI can show an error.
 */
public class EasyTpClient implements ClientModInitializer {
    public static final String MOD_ID = "easytp";

    private static final long ERROR_WINDOW_MS = 2000;

    private static KeyBinding openKey;

    // Simple status line shown at the bottom of the GUI.
    private static String statusText = "";
    private static boolean statusError = false;
    private static long lastSendTime = 0;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.easytp.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    client.setScreen(new EasyTpScreen());
                }
            }
        });

        // If the server answers our /tp with an error shortly after we sent it, surface it.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (System.currentTimeMillis() - lastSendTime > ERROR_WINDOW_MS) {
                return;
            }
            String text = message.getString().toLowerCase(Locale.ROOT);
            if (text.contains("unknown or incomplete") || text.contains("permission")) {
                setStatus("Server rejected /tp - you need OP / permission", true);
            } else if (text.contains("no player was found") || text.contains("no entity was found")) {
                setStatus("Player not found on the server", true);
            }
        });
    }

    public static void markCommandSent() {
        lastSendTime = System.currentTimeMillis();
    }

    public static void setStatus(String text, boolean error) {
        statusText = text;
        statusError = error;
    }

    public static String getStatusText() {
        return statusText;
    }

    public static boolean isStatusError() {
        return statusError;
    }

    public static void clearStatus() {
        statusText = "";
        statusError = false;
    }
}
