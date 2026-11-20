package com.friendsmod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class FriendsScreen extends Screen {
    private enum Tab { FRIENDS, REQUESTS, ADD, SETTINGS }

    private record Row(String name, int kind) {} // 0 = znajomy, 1 = przychodzace, 2 = wyslane

    private static final int PANEL_W = 380;
    private static final int LIST_TOP = 50;
    private static final int ROW_H = 24;

    private static Tab lastTab = Tab.FRIENDS;
    private static String addText = "";

    private final Screen parent;
    private Tab tab = lastTab;
    private int page = 0;
    private int maxPage = 0;
    private int left;
    private int seenVersion;
    private boolean listening = false;
    private String status = "";
    private boolean statusOk = true;
    private TextFieldWidget nameField;
    private final List<Row> visible = new ArrayList<>();

    public FriendsScreen(Screen parent) {
        super(Text.literal("Friends"));
        this.parent = parent;
    }

    private String tabLabel(Tab t) {
        return switch (t) {
            case FRIENDS -> "Znajomi";
            case REQUESTS -> FriendManager.incoming.isEmpty() ? "Zaproszenia"
                    : "Zaproszenia (" + FriendManager.incoming.size() + ")";
            case ADD -> "Dodaj";
            case SETTINGS -> "Ustawienia";
        };
    }

    @Override
    protected void init() {
        seenVersion = FriendManager.version;
        visible.clear();
        left = (width - PANEL_W) / 2;

        Tab[] tabs = Tab.values();
        int tw = PANEL_W / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            final Tab t = tabs[i];
            ButtonWidget b = ButtonWidget.builder(Text.literal(tabLabel(t)), btn -> {
                tab = t;
                lastTab = t;
                page = 0;
                status = "";
                listening = false;
                clearAndInit();
            }).dimensions(left + i * tw, 20, tw - 2, 20).build();
            b.active = (t != tab);
            addDrawableChild(b);
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Zamknij"), b -> close())
                .dimensions(width / 2 - 40, height - 26, 80, 20).build());

        if (tab == Tab.ADD) initAdd();
        else if (tab == Tab.SETTINGS) initSettings();
        else initList();
    }

    private void initSettings() {
        Text label = listening ? Text.literal("> nacisnij klawisz <") : FriendsMod.OPEN_KEY.getBoundKeyLocalizedText();
        addDrawableChild(ButtonWidget.builder(label, b -> {
            listening = true;
            clearAndInit();
        }).dimensions(left + 170, 62, 130, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), b -> {
            setOpenKey(FriendsMod.OPEN_KEY.getDefaultKey());
            listening = false;
            clearAndInit();
        }).dimensions(left + 306, 62, 60, 20).build());

        addDrawableChild(new MeterSlider(left, 100, PANEL_W, 20));

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Metry nad nickiem: " + (FriendManager.nametagMeters ? "WLACZONE" : "WYLACZONE")), b -> {
            FriendManager.nametagMeters = !FriendManager.nametagMeters;
            FriendManager.save();
            clearAndInit();
        }).dimensions(left, 126, PANEL_W, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Znaczniki na ekranie: " + (FriendManager.markers ? "WLACZONE" : "WYLACZONE")), b -> {
            FriendManager.markers = !FriendManager.markers;
            FriendManager.save();
            clearAndInit();
        }).dimensions(left, 152, PANEL_W, 20).build());
    }

    /** Suwak: od ilu metrow pokazywac lokalizacje i metry znajomego (0 - 1000, krok 5). */
    private static class MeterSlider extends SliderWidget {
        MeterSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), FriendManager.showFromMeters / 1000.0);
            updateMessage();
        }

        private int meters() {
            return (int) Math.round(value * 200.0) * 5;
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Pokazuj znajomych od: " + meters() + " m"));
        }

        @Override
        protected void applyValue() {
            FriendManager.showFromMeters = meters();
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            FriendManager.save();
        }
    }

    private void setOpenKey(InputUtil.Key key) {
        FriendsMod.OPEN_KEY.setBoundKey(key);
        KeyBinding.updateKeysByCode();
        client.options.write();
    }

    private void initAdd() {
        nameField = new TextFieldWidget(textRenderer, left, 62, PANEL_W - 110, 20, Text.literal("Nick"));
        nameField.setMaxLength(16);
        nameField.setPlaceholder(Text.literal("Nick gracza"));
        nameField.setText(addText);
        nameField.setChangedListener(s -> addText = s);
        addDrawableChild(nameField);
        setInitialFocus(nameField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Wyslij zaproszenie"), b -> submit())
                .dimensions(left + PANEL_W - 105, 62, 105, 20).build());
    }

    private void submit() {
        FriendManager.Result r = FriendManager.sendInvite(client, nameField.getText().trim());
        status = r.msg();
        statusOk = r.ok();
        if (r.ok()) {
            addText = "";
            nameField.setText("");
        }
    }

    private void initList() {
        List<Row> rows = new ArrayList<>();
        if (tab == Tab.FRIENDS) {
            for (String f : FriendManager.friends) rows.add(new Row(f, 0));
        } else {
            for (String n : FriendManager.incoming) rows.add(new Row(n, 1));
            for (String n : FriendManager.outgoing) rows.add(new Row(n, 2));
        }

        int perPage = Math.max(1, (height - LIST_TOP - 36) / ROW_H);
        maxPage = Math.max(0, (rows.size() - 1) / perPage);
        if (page > maxPage) page = maxPage;
        int start = page * perPage;
        int end = Math.min(rows.size(), start + perPage);
        visible.addAll(rows.subList(start, end));

        for (int i = 0; i < visible.size(); i++) {
            final Row r = visible.get(i);
            int y = LIST_TOP + i * ROW_H + 2;
            int right = left + PANEL_W;
            switch (r.kind()) {
                case 0 -> addDrawableChild(ButtonWidget.builder(Text.literal("Usun"), b -> {
                    FriendManager.remove(r.name());
                    clearAndInit();
                }).dimensions(right - 60, y, 60, 20).build());
                case 1 -> {
                    addDrawableChild(ButtonWidget.builder(Text.literal("Akceptuj"), b -> {
                        FriendManager.accept(r.name());
                        clearAndInit();
                    }).dimensions(right - 130, y, 66, 20).build());
                    addDrawableChild(ButtonWidget.builder(Text.literal("Odrzuc"), b -> {
                        FriendManager.decline(r.name());
                        clearAndInit();
                    }).dimensions(right - 60, y, 60, 20).build());
                }
                default -> addDrawableChild(ButtonWidget.builder(Text.literal("Anuluj"), b -> {
                    FriendManager.cancel(r.name());
                    clearAndInit();
                }).dimensions(right - 60, y, 60, 20).build());
            }
        }

        ButtonWidget prev = ButtonWidget.builder(Text.literal("<"), b -> { page--; clearAndInit(); })
                .dimensions(left, height - 26, 40, 20).build();
        prev.active = page > 0;
        addDrawableChild(prev);
        ButtonWidget next = ButtonWidget.builder(Text.literal(">"), b -> { page++; clearAndInit(); })
                .dimensions(left + PANEL_W - 40, height - 26, 40, 20).build();
        next.active = page < maxPage;
        addDrawableChild(next);
    }

    @Override
    public void tick() {
        if (seenVersion != FriendManager.version) clearAndInit(); // np. nowe zaproszenie
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) setOpenKey(InputUtil.fromKeyCode(keyCode, scanCode));
            listening = false;
            clearAndInit();
            return true;
        }
        if (tab == Tab.ADD && nameField != null && nameField.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta); // tlo + widgety

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Friends"), width / 2, 6, 0xFFFFFFFF);

        if (tab == Tab.SETTINGS) {
            ctx.drawTextWithShadow(textRenderer, "Klawisz otwierania menu:", left, 68, 0xFFFFFFFF);
            ctx.drawTextWithShadow(textRenderer, "Kliknij przycisk, potem nacisnij wybrany klawisz (ESC = anuluj).", left, 87, 0xFF888888);
            ctx.drawTextWithShadow(textRenderer, "Znaczniki pokazuja kierunek i dystans do znajomych poza zasiegiem renderowania.", left, 180, 0xFF888888);
            return;
        }

        if (tab == Tab.ADD) {
            ctx.drawTextWithShadow(textRenderer, "Wpisz nick gracza, ktory jest teraz online.", left, 46, 0xFFAAAAAA);
            ctx.drawTextWithShadow(textRenderer, "Obie strony musza miec zainstalowany ten mod.", left, 92, 0xFF888888);
            if (!status.isEmpty()) {
                ctx.drawTextWithShadow(textRenderer, status, left, 108, statusOk ? 0xFF55FF55 : 0xFFFF5555);
            }
            return;
        }

        if (visible.isEmpty()) {
            String empty = tab == Tab.FRIENDS ? "Brak znajomych. Uzyj zakladki Dodaj." : "Brak zaproszen.";
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(empty), width / 2, LIST_TOP + 20, 0xFFAAAAAA);
            return;
        }

        for (int i = 0; i < visible.size(); i++) {
            Row r = visible.get(i);
            int y = LIST_TOP + i * ROW_H;
            ctx.fill(left, y + ROW_H - 1, left + PANEL_W, y + ROW_H, 0x33FFFFFF);
            ctx.drawTextWithShadow(textRenderer, r.name(), left + 2, y + 3, 0xFFFFFFFF);

            String sub;
            int color;
            if (r.kind() == 1) {
                sub = "chce zostac Twoim znajomym";
                color = 0xFFFFAA00;
            } else if (r.kind() == 2) {
                sub = "zaproszenie wyslane - oczekuje";
                color = 0xFF888888;
            } else {
                FriendManager.Info info = FriendManager.info(client, r.name());
                if (!info.online) {
                    sub = "offline";
                    color = 0xFF888888;
                } else if (!info.known) {
                    sub = "online - czekam na lokalizacje";
                    color = 0xFF55FF55;
                } else if (info.near) {
                    sub = "w poblizu (do " + FriendManager.showFromMeters + " m)";
                    color = 0xFF55FF55;
                } else {
                    String where = info.sameDim ? (int) info.dist + " m" : FriendManager.dimLabel(info.dim);
                    sub = info.coords() + "  (" + where + ")";
                    color = 0xFFFFFF55;
                }
            }
            ctx.drawTextWithShadow(textRenderer, sub, left + 2, y + 13, color);
        }

        if (maxPage > 0) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal((page + 1) + "/" + (maxPage + 1)),
                    width / 2, height - 40, 0xFFAAAAAA);
        }
    }
}
