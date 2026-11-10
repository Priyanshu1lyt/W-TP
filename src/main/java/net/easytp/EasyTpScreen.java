package net.easytp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Easy TP GUI.
 * LEFT  = WHO gets teleported (EVERYONE row on top, multi-select).
 * RIGHT = WHERE they get teleported (one destination).
 *
 * Left list: click a player = teleport them now. Shift+click = toggle selection.
 * Clicking a selected player (when 2+ are selected) teleports all selected.
 * Click EVERYONE = select all online players and teleport them. Shift+click EVERYONE = select/clear all only.
 */
public class EasyTpScreen extends Screen {
    private static final int ROW_H = 22;
    private static final int HEAD = 12;

    private static final int COLOR_PANEL = 0xCC101014;
    // left list (targets)
    private static final int COLOR_TARGET = 0x6638C060;
    private static final int COLOR_TARGET_BORDER = 0xFF4ADE80;
    // right list (destination)
    private static final int COLOR_DEST = 0x663B82F6;
    private static final int COLOR_DEST_BORDER = 0xFF60A5FA;
    // EVERYONE row
    private static final int COLOR_EVERYONE_IDLE = 0x33FFC83D;
    private static final int COLOR_EVERYONE_ACTIVE = 0x99FFC83D;
    private static final int COLOR_EVERYONE_BORDER = 0xFFFFC83D;

    /** name == null means the EVERYONE row. */
    private record Row(String name, PlayerListEntry entry, int x, int y, int w, boolean left) {}

    private final List<PlayerListEntry> players = new ArrayList<>();
    private final List<String> playerNames = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();

    private String destination = null;
    private final Set<String> targets = new LinkedHashSet<>();

    private int leftScroll = 0;
    private int rightScroll = 0;

    private int colW;
    private int leftX;
    private int rightX;
    private int listTop;
    private int listBottom;
    private int visibleRows;

    public EasyTpScreen() {
        super(Text.literal("Easy TP"));
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        colW = Math.max(100, Math.min(180, (width - 60) / 2));
        int gap = 20;
        leftX = width / 2 - gap / 2 - colW;
        rightX = width / 2 + gap / 2;
        listTop = 52;
        listBottom = Math.max(listTop + ROW_H, height - 60);
        visibleRows = Math.max(1, (listBottom - listTop) / ROW_H);

        refreshPlayers();
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        rows.clear();

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(width / 2 - 40, height - 30, 80, 20)
                .build());

        buildColumn(true);
        buildColumn(false);
    }

    /** Number of rows in a column. The left column has an extra EVERYONE row on top. */
    private int itemCount(boolean left) {
        if (players.isEmpty()) {
            return 0;
        }
        return left ? players.size() + 1 : players.size();
    }

    private void buildColumn(boolean left) {
        int count = itemCount(left);
        int offset = left ? leftScroll : rightScroll;
        int x = left ? leftX : rightX;
        for (int i = 0; i < visibleRows; i++) {
            int idx = offset + i;
            if (idx >= count) {
                break;
            }
            int y = listTop + i * ROW_H;

            final String name = left
                    ? (idx == 0 ? null : playerNames.get(idx - 1))
                    : playerNames.get(idx);
            final PlayerListEntry entry = name == null ? null : players.get(left ? idx - 1 : idx);

            ButtonWidget button = ButtonWidget.builder(Text.empty(), b -> {
                        if (!left) {
                            onDestinationClicked(name);
                        } else if (name == null) {
                            onEveryoneClicked();
                        } else {
                            onTargetClicked(name);
                        }
                    })
                    .dimensions(x, y, colW, ROW_H - 2)
                    .build();
            addDrawableChild(button);
            rows.add(new Row(name, entry, x, y, colW, left));
        }
    }

    // ---------------------------------------------------------- player list

    /** @return true if the online player list changed. */
    private boolean refreshPlayers() {
        ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        List<PlayerListEntry> fresh = new ArrayList<>();
        if (handler != null) {
            fresh.addAll(handler.getPlayerList());
        }
        fresh.sort(Comparator.comparing(e -> e.getProfile().name().toLowerCase()));

        List<String> freshNames = new ArrayList<>();
        for (PlayerListEntry e : fresh) {
            freshNames.add(e.getProfile().name());
        }

        if (freshNames.equals(playerNames)) {
            return false;
        }

        players.clear();
        players.addAll(fresh);
        playerNames.clear();
        playerNames.addAll(freshNames);

        // Drop selections of players who left.
        if (destination != null && !playerNames.contains(destination)) {
            destination = null;
        }
        targets.retainAll(playerNames);

        leftScroll = clampScroll(leftScroll, itemCount(true));
        rightScroll = clampScroll(rightScroll, itemCount(false));
        return true;
    }

    private int clampScroll(int value, int count) {
        int max = Math.max(0, count - visibleRows);
        return Math.max(0, Math.min(max, value));
    }

    private boolean everyoneSelected() {
        return !playerNames.isEmpty() && targets.containsAll(playerNames);
    }

    @Override
    public void tick() {
        super.tick();
        if (refreshPlayers()) {
            rebuild();
        }
    }

    // --------------------------------------------------------------- actions

    /** Shift check straight from GLFW (Screen.hasShiftDown was removed in 1.21.11). */
    private static boolean isShiftDown() {
        long window = GLFW.glfwGetCurrentContext();
        if (window == 0L) {
            return false;
        }
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    /** RIGHT list: choose (or un-choose) the destination. */
    private void onDestinationClicked(String name) {
        if (!playerNames.contains(name)) {
            return; // player is no longer online
        }
        destination = name.equals(destination) ? null : name;
        EasyTpClient.clearStatus();
    }

    /** LEFT list: a single player. */
    private void onTargetClicked(String name) {
        if (!playerNames.contains(name)) {
            return; // player is no longer online
        }

        if (isShiftDown()) {
            if (!targets.remove(name)) {
                targets.add(name);
            }
            return;
        }

        if (targets.contains(name) && targets.size() > 1) {
            teleport(new ArrayList<>(targets));
        } else {
            teleport(List.of(name));
        }
    }

    /** LEFT list: the EVERYONE row. */
    private void onEveryoneClicked() {
        if (playerNames.isEmpty()) {
            return;
        }

        if (isShiftDown()) {
            // Select / clear everyone without teleporting.
            if (everyoneSelected()) {
                targets.clear();
            } else {
                targets.addAll(playerNames);
            }
            return;
        }

        targets.addAll(playerNames);
        teleport(new ArrayList<>(playerNames));
    }

    private void teleport(List<String> names) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null || client.player == null) {
            return;
        }
        if (destination == null) {
            EasyTpClient.setStatus("Select a destination on the right first", true);
            return;
        }
        if (!playerNames.contains(destination)) {
            destination = null;
            EasyTpClient.setStatus("Destination player is no longer online", true);
            return;
        }
        if (names.isEmpty()) {
            EasyTpClient.setStatus("Select a player to teleport", true);
            return;
        }

        int sent = 0;
        String last = null;
        // LinkedHashSet removes duplicates so we never send the same command twice.
        for (String target : new LinkedHashSet<>(names)) {
            if (!playerNames.contains(target)) {
                continue; // went offline
            }
            if (target.equals(destination)) {
                continue; // already at the destination: harmless no-op, no command, no error
            }
            EasyTpClient.markCommandSent();
            handler.sendChatCommand("tp " + target + " " + destination);
            sent++;
            last = target;
        }

        if (sent == 1) {
            EasyTpClient.setStatus("/tp " + last + " " + destination, false);
        } else if (sent > 1) {
            EasyTpClient.setStatus("Sent " + sent + " teleports to " + destination, false);
        }
    }

    // ------------------------------------------------------------- scrolling

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int delta = verticalAmount > 0 ? -1 : 1;
        boolean left = mouseX < width / 2.0;
        int count = itemCount(left);
        if (count <= visibleRows) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (left) {
            leftScroll = clampScroll(leftScroll + delta, count);
        } else {
            rightScroll = clampScroll(rightScroll + delta, count);
        }
        rebuild();
        return true;
    }

    // ------------------------------------------------------------- rendering

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        drawPanel(context, leftX, "Teleport");
        drawPanel(context, rightX, "Destination");
    }

    private void drawPanel(DrawContext context, int x, String title) {
        context.fill(x - 4, listTop - 20, x + colW + 4, listBottom + 4, COLOR_PANEL);
        context.drawTextWithShadow(textRenderer, Text.literal(title), x + 2, listTop - 14, 0xFFFFFFFF);
    }

    /** 1px outline made from fills (DrawContext.drawBorder was removed in 1.21.11). */
    private static void drawOutline(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);             // top
        context.fill(x, y + h - 1, x + w, y + h, color);     // bottom
        context.fill(x, y + 1, x + 1, y + h - 1, color);     // left
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color); // right
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        Text titleText = Text.literal("Easy TP");
        context.drawTextWithShadow(textRenderer, titleText,
                width / 2 - textRenderer.getWidth(titleText) / 2, 14, 0xFFFFFFFF);
        Text hint = Text.literal("Pick a destination on the right, then click who to teleport (Shift+click = multi-select)");
        context.drawTextWithShadow(textRenderer, hint,
                width / 2 - textRenderer.getWidth(hint) / 2, 28, 0xFFAAAAAA);

        String self = MinecraftClient.getInstance().getSession().getUsername();
        int h = ROW_H - 2;

        for (Row row : rows) {
            // EVERYONE row
            if (row.name() == null) {
                boolean active = everyoneSelected();
                context.fill(row.x(), row.y(), row.x() + row.w(), row.y() + h,
                        active ? COLOR_EVERYONE_ACTIVE : COLOR_EVERYONE_IDLE);
                drawOutline(context, row.x(), row.y(), row.w(), h, COLOR_EVERYONE_BORDER);

                Text label = Text.literal("EVERYONE (" + playerNames.size() + ")")
                        .formatted(Formatting.GOLD, Formatting.BOLD);
                context.drawTextWithShadow(textRenderer, label,
                        row.x() + row.w() / 2 - textRenderer.getWidth(label) / 2,
                        row.y() + (h - 8) / 2, 0xFFFFFFFF);
                continue;
            }

            boolean selected = row.left() ? targets.contains(row.name()) : row.name().equals(destination);
            if (selected) {
                context.fill(row.x(), row.y(), row.x() + row.w(), row.y() + h,
                        row.left() ? COLOR_TARGET : COLOR_DEST);
                drawOutline(context, row.x(), row.y(), row.w(), h,
                        row.left() ? COLOR_TARGET_BORDER : COLOR_DEST_BORDER);
            }

            PlayerSkinDrawer.draw(context, row.entry().getSkinTextures(), row.x() + 5, row.y() + (h - HEAD) / 2, HEAD);

            String label = row.name().equalsIgnoreCase(self) ? row.name() + " (you)" : row.name();
            context.drawTextWithShadow(textRenderer, Text.literal(label),
                    row.x() + 5 + HEAD + 5, row.y() + (h - 8) / 2, 0xFFFFFFFF);
        }

        if (players.isEmpty()) {
            Text empty = Text.literal("No players online");
            context.drawTextWithShadow(textRenderer, empty,
                    leftX + colW / 2 - textRenderer.getWidth(empty) / 2, listTop + 8, 0xFF888888);
            context.drawTextWithShadow(textRenderer, empty,
                    rightX + colW / 2 - textRenderer.getWidth(empty) / 2, listTop + 8, 0xFF888888);
        }

        drawScrollbar(context, leftX, leftScroll, itemCount(true));
        drawScrollbar(context, rightX, rightScroll, itemCount(false));

        String status = EasyTpClient.getStatusText();
        if (!status.isEmpty()) {
            Text statusText = Text.literal(status);
            int color = EasyTpClient.isStatusError() ? 0xFFFF5555 : 0xFF55FF55;
            context.drawTextWithShadow(textRenderer, statusText,
                    width / 2 - textRenderer.getWidth(statusText) / 2, height - 48, color);
        }
    }

    private void drawScrollbar(DrawContext context, int x, int scroll, int count) {
        if (count <= visibleRows) {
            return;
        }
        int trackH = visibleRows * ROW_H;
        int barH = Math.max(12, trackH * visibleRows / count);
        int maxScroll = count - visibleRows;
        int barY = listTop + (trackH - barH) * scroll / maxScroll;
        context.fill(x + colW + 1, barY, x + colW + 3, barY + barH, 0xFF888888);
    }
}
