package net.easytp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Easy TP GUI.
 * LEFT  = destination (pick one).
 * RIGHT = targets. Click a player to teleport them to the destination instantly.
 *         Shift+click toggles multi-selection; clicking a selected player teleports all selected.
 */
public class EasyTpScreen extends Screen {
    private static final int ROW_H = 22;
    private static final int HEAD = 12;

    private static final int COLOR_PANEL = 0xCC101014;
    private static final int COLOR_DEST = 0x6638C060;
    private static final int COLOR_DEST_BORDER = 0xFF4ADE80;
    private static final int COLOR_TARGET = 0x663B82F6;
    private static final int COLOR_TARGET_BORDER = 0xFF60A5FA;

    private record Row(String name, PlayerListEntry entry, int x, int y, int w, boolean left) {}

    private final List<PlayerListEntry> players = new ArrayList<>();
    private final List<String> playerNames = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();

    private String destination = null;
    private final Set<String> targets = new LinkedHashSet<>();

    private int destScroll = 0;
    private int targetScroll = 0;

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

    private void buildColumn(boolean left) {
        int offset = left ? destScroll : targetScroll;
        int x = left ? leftX : rightX;
        for (int i = 0; i < visibleRows; i++) {
            int idx = offset + i;
            if (idx >= players.size()) {
                break;
            }
            PlayerListEntry entry = players.get(idx);
            String name = playerNames.get(idx);
            int y = listTop + i * ROW_H;

            ButtonWidget button = ButtonWidget.builder(Text.empty(), b -> onRowClicked(name, left))
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

        destScroll = clampScroll(destScroll);
        targetScroll = clampScroll(targetScroll);
        return true;
    }

    private int clampScroll(int value) {
        int max = Math.max(0, players.size() - visibleRows);
        return Math.max(0, Math.min(max, value));
    }

    @Override
    public void tick() {
        super.tick();
        if (refreshPlayers()) {
            rebuild();
        }
    }

    // --------------------------------------------------------------- actions

    private void onRowClicked(String name, boolean left) {
        if (!playerNames.contains(name)) {
            return; // player is no longer online
        }
        if (left) {
            destination = name.equals(destination) ? null : name;
            EasyTpClient.clearStatus();
            return;
        }

        if (hasShiftDown()) {
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

    private void teleport(List<String> names) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null || client.player == null) {
            return;
        }
        if (destination == null) {
            EasyTpClient.setStatus("Select a destination on the left first", true);
            return;
        }
        if (!playerNames.contains(destination)) {
            destination = null;
            EasyTpClient.setStatus("Destination player is no longer online", true);
            return;
        }

        String self = client.getSession().getUsername();
        int sent = 0;
        for (String target : names) {
            if (!playerNames.contains(target)) {
                continue;
            }
            if (target.equalsIgnoreCase(self)) {
                EasyTpClient.setStatus("You can't teleport yourself", true);
                continue;
            }
            if (target.equals(destination)) {
                EasyTpClient.setStatus("Target and destination are the same player", true);
                continue;
            }
            EasyTpClient.markCommandSent();
            handler.sendChatCommand("tp " + target + " " + destination);
            EasyTpClient.setStatus("/tp " + target + " " + destination, false);
            sent++;
        }
        if (sent > 1) {
            EasyTpClient.setStatus("Sent " + sent + " teleports to " + destination, false);
        }
    }

    // ------------------------------------------------------------- scrolling

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0 || players.size() <= visibleRows) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int delta = verticalAmount > 0 ? -1 : 1;
        if (mouseX < width / 2.0) {
            destScroll = clampScroll(destScroll + delta);
        } else {
            targetScroll = clampScroll(targetScroll + delta);
        }
        rebuild();
        return true;
    }

    // ------------------------------------------------------------- rendering

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        drawPanel(context, leftX, "Destination");
        drawPanel(context, rightX, "Target");
    }

    private void drawPanel(DrawContext context, int x, String title) {
        context.fill(x - 4, listTop - 20, x + colW + 4, listBottom + 4, COLOR_PANEL);
        context.drawTextWithShadow(textRenderer, Text.literal(title), x + 2, listTop - 14, 0xFFFFFFFF);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        Text titleText = Text.literal("Easy TP");
        context.drawTextWithShadow(textRenderer, titleText,
                width / 2 - textRenderer.getWidth(titleText) / 2, 14, 0xFFFFFFFF);
        Text hint = Text.literal("Pick a destination, then click a player to teleport them (Shift+click = multi-select)");
        context.drawTextWithShadow(textRenderer, hint,
                width / 2 - textRenderer.getWidth(hint) / 2, 28, 0xFFAAAAAA);

        String self = MinecraftClient.getInstance().getSession().getUsername();

        for (Row row : rows) {
            boolean selected = row.left() ? row.name().equals(destination) : targets.contains(row.name());
            int h = ROW_H - 2;
            if (selected) {
                context.fill(row.x(), row.y(), row.x() + row.w(), row.y() + h,
                        row.left() ? COLOR_DEST : COLOR_TARGET);
                context.drawBorder(row.x(), row.y(), row.w(), h,
                        row.left() ? COLOR_DEST_BORDER : COLOR_TARGET_BORDER);
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

        drawScrollbar(context, leftX, destScroll);
        drawScrollbar(context, rightX, targetScroll);

        String status = EasyTpClient.getStatusText();
        if (!status.isEmpty()) {
            Text statusText = Text.literal(status);
            int color = EasyTpClient.isStatusError() ? 0xFFFF5555 : 0xFF55FF55;
            context.drawTextWithShadow(textRenderer, statusText,
                    width / 2 - textRenderer.getWidth(statusText) / 2, height - 48, color);
        }
    }

    private void drawScrollbar(DrawContext context, int x, int scroll) {
        if (players.size() <= visibleRows) {
            return;
        }
        int trackH = visibleRows * ROW_H;
        int barH = Math.max(12, trackH * visibleRows / players.size());
        int maxScroll = players.size() - visibleRows;
        int barY = listTop + (trackH - barH) * scroll / maxScroll;
        context.fill(x + colW + 1, barY, x + colW + 3, barY + barH, 0xFF888888);
    }
}
