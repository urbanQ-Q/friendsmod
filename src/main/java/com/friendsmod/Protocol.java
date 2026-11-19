package com.friendsmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mod jest czysto klientowy, wiec komunikacja idzie przez prywatne wiadomosci (/msg).
 * Format: msf1;TYP;Nick[;x;y;z;wymiar]
 * TYP: INV (zaproszenie), ACC (akceptacja), DEC (odrzucenie), REM (usuniecie), POS (pozycja)
 * Wiadomosci z tym znacznikiem sa ukrywane w czacie u odbiorcy i u nadawcy.
 */
public final class Protocol {
    private static final String MARK = "msf1;";
    private static final Pattern P = Pattern.compile(
            "msf1;(INV|ACC|DEC|REM|POS);([A-Za-z0-9_]{1,16})(?:;(-?\\d+);(-?\\d+);(-?\\d+);([a-z0-9_.:/-]+))?");

    private static final int SEND_INTERVAL_TICKS = 30;   // 1 wiadomosc na 1,5 s (anty-spam)
    private static final int POS_INTERVAL_TICKS = 100;   // pozycja co 5 s
    private static final int MAX_QUEUE = 30;

    private static final ArrayDeque<String[]> QUEUE = new ArrayDeque<>();
    private static int cooldown = 0;
    private static int posTimer = 0;

    private Protocol() {}

    public static void clear() {
        QUEUE.clear();
        cooldown = 0;
        posTimer = 0;
    }

    public static boolean isOnline(MinecraftClient mc, String name) {
        if (mc.getNetworkHandler() == null) return false;
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            if (e.getProfile().getName().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ wysylanie

    public static void send(String type, String target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        enqueue(target, MARK + type + ";" + mc.getSession().getUsername());
    }

    private static void enqueue(String target, String payload) {
        if (payload.startsWith(MARK + "POS")) {
            QUEUE.removeIf(p -> p[0].equalsIgnoreCase(target) && p[1].startsWith(MARK + "POS"));
        }
        if (QUEUE.size() >= MAX_QUEUE) return;
        QUEUE.add(new String[]{target, payload});
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            QUEUE.clear();
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        } else if (!QUEUE.isEmpty()) {
            String[] p = QUEUE.poll();
            mc.getNetworkHandler().sendChatCommand(FriendManager.messageCommand + " " + p[0] + " " + p[1]);
            cooldown = SEND_INTERVAL_TICKS;
        }

        if (++posTimer >= POS_INTERVAL_TICKS) {
            posTimer = 0;
            broadcastPositions(mc);
        }
    }

    private static void broadcastPositions(MinecraftClient mc) {
        String me = mc.getSession().getUsername();
        Vec3d pos = mc.player.getPos();
        String dim = FriendManager.currentDim(mc);
        for (String f : FriendManager.friends) {
            if (f.equalsIgnoreCase(me) || !isOnline(mc, f)) continue;
            FriendManager.Info i = FriendManager.info(mc, f);
            if (i.near) continue; // jest blisko (<= 20) - lokalizacja niepotrzebna
            enqueue(f, String.format(Locale.ROOT, "%s%s;%s;%d;%d;%d;%s", MARK, "POS", me,
                    (int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z), dim));
        }
    }

    // ------------------------------------------------------------ odbieranie

    /** @return true jesli wiadomosc nalezy do protokolu (i ma byc ukryta). */
    public static boolean handleIncoming(MinecraftClient mc, Text message) {
        String s = message.getString();
        if (!s.contains(MARK)) return false;
        if (mc.player == null) return true;

        String key = null;
        Object[] args = new Object[0];
        if (message.getContent() instanceof TranslatableTextContent t) {
            key = t.getKey();
            args = t.getArgs();
        }
        // echo naszej wlasnej wiadomosci ("You whisper to ...")
        if ("commands.message.display.outgoing".equals(key)) return true;

        Matcher m = P.matcher(s);
        if (!m.find()) return true;
        String type = m.group(1);
        String name = m.group(2);

        String me = mc.getSession().getUsername();
        if (name.equalsIgnoreCase(me)) return true;

        // Weryfikacja nadawcy (vanilla): nick z wiadomosci musi zgadzac sie z prawdziwym nadawca /msg
        if ("commands.message.display.incoming".equals(key) && args.length > 0 && args[0] instanceof Text sender) {
            if (!sender.getString().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) return true;
        }

        switch (type) {
            case "INV" -> onInvite(mc, name);
            case "ACC" -> {
                if (FriendManager.outgoing.contains(name)) {
                    FriendManager.outgoing.remove(name);
                    FriendManager.friends.add(name);
                    FriendManager.changed();
                    notify(mc, name + " zaakceptowal/a Twoje zaproszenie!");
                }
            }
            case "DEC" -> {
                if (FriendManager.outgoing.contains(name)) {
                    FriendManager.outgoing.remove(name);
                    FriendManager.changed();
                    notify(mc, name + " odrzucil/a Twoje zaproszenie.");
                }
            }
            case "REM" -> {
                if (FriendManager.friends.contains(name)) {
                    FriendManager.friends.remove(name);
                    FriendManager.locations.remove(name);
                    FriendManager.changed();
                    notify(mc, name + " usunal/a Cie ze znajomych.");
                }
            }
            case "POS" -> {
                if (FriendManager.friends.contains(name) && m.group(3) != null) {
                    try {
                        FriendManager.locations.put(name, new FriendManager.Loc(
                                Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)),
                                Double.parseDouble(m.group(5)), FriendManager.normDim(m.group(6)),
                                System.currentTimeMillis()));
                    } catch (NumberFormatException ignored) {}
                }
            }
            default -> {}
        }
        return true;
    }

    private static void onInvite(MinecraftClient mc, String name) {
        if (FriendManager.friends.contains(name)) {
            send("ACC", name); // juz jestesmy znajomymi - potwierdz
            return;
        }
        if (FriendManager.outgoing.contains(name)) {
            // wzajemne zaproszenia = od razu znajomi
            FriendManager.outgoing.remove(name);
            FriendManager.friends.add(name);
            send("ACC", name);
            FriendManager.changed();
            notify(mc, "Jestes teraz znajomym z " + name + "!");
            return;
        }
        if (FriendManager.incoming.add(name)) {
            FriendManager.changed();
            notify(mc, name + " wyslal/a Ci zaproszenie do znajomych. Otworz menu Friends (J), aby zaakceptowac lub odrzucic.");
        }
    }

    private static void notify(MinecraftClient mc, String msg) {
        if (mc.player == null) return;
        mc.player.sendMessage(Text.literal("[Friends] ").formatted(Formatting.GOLD)
                .append(Text.literal(msg).formatted(Formatting.WHITE)), false);
    }
}
