package com.friendsmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public final class FriendManager {
    public static final double FAR_DISTANCE = 20.0;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("friendsmod.json");

    public static final Set<String> friends = newSet();
    public static final Set<String> incoming = newSet();
    public static final Set<String> outgoing = newSet();
    public static final Map<String, Loc> locations = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    public static String messageCommand = "msg";
    /** Zwiekszane przy kazdej zmianie list - ekran odswieza sie po zmianie. */
    public static int version = 0;

    public record Loc(double x, double y, double z, String dim, long time) {}
    public record Result(boolean ok, String msg) {}

    private static class Data {
        List<String> friends = new ArrayList<>();
        List<String> incoming = new ArrayList<>();
        List<String> outgoing = new ArrayList<>();
        String messageCommand = "msg";
    }

    private static Set<String> newSet() {
        return new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    }

    // ------------------------------------------------------------ zapis/odczyt

    public static void load() {
        if (!Files.exists(FILE)) { save(); return; }
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Data d = GSON.fromJson(r, Data.class);
            if (d == null) return;
            friends.addAll(d.friends);
            incoming.addAll(d.incoming);
            outgoing.addAll(d.outgoing);
            if (d.messageCommand != null && !d.messageCommand.isBlank()) messageCommand = d.messageCommand;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        Data d = new Data();
        d.friends.addAll(friends);
        d.incoming.addAll(incoming);
        d.outgoing.addAll(outgoing);
        d.messageCommand = messageCommand;
        try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(d, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void changed() {
        version++;
        save();
    }

    // ------------------------------------------------------------ akcje

    public static Result sendInvite(MinecraftClient mc, String name) {
        String me = mc.getSession().getUsername();
        if (!NAME.matcher(name).matches()) return new Result(false, "Niepoprawny nick.");
        if (name.equalsIgnoreCase(me)) return new Result(false, "Nie mozesz dodac samego siebie.");
        if (friends.contains(name)) return new Result(false, name + " jest juz Twoim znajomym.");
        if (outgoing.contains(name)) return new Result(false, "Zaproszenie do " + name + " juz wyslane.");
        if (incoming.contains(name)) {
            accept(name);
            return new Result(true, "Mial/a zaproszenie do Ciebie - zaakceptowano: " + name);
        }
        if (!Protocol.isOnline(mc, name)) return new Result(false, name + " musi byc online na serwerze.");
        outgoing.add(name);
        Protocol.send("INV", name);
        changed();
        return new Result(true, "Wyslano zaproszenie do " + name + ".");
    }

    public static void accept(String name) {
        incoming.remove(name);
        outgoing.remove(name);
        friends.add(name);
        Protocol.send("ACC", name);
        changed();
    }

    public static void decline(String name) {
        incoming.remove(name);
        Protocol.send("DEC", name);
        changed();
    }

    public static void cancel(String name) {
        outgoing.remove(name);
        changed();
    }

    public static void remove(String name) {
        friends.remove(name);
        locations.remove(name);
        Protocol.send("REM", name);
        changed();
    }

    // ------------------------------------------------------------ lokalizacja

    public static String normDim(String d) {
        return d.startsWith("minecraft:") ? d.substring("minecraft:".length()) : d;
    }

    public static String currentDim(MinecraftClient mc) {
        return normDim(mc.world.getRegistryKey().getValue().toString());
    }

    public static String dimLabel(String d) {
        return switch (d) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "End";
            default -> d;
        };
    }

    public static class Info {
        public boolean online;
        public boolean known;     // znamy pozycje (encja w zasiegu lub niedawna wiadomosc POS)
        public boolean sameDim;
        public boolean far;       // inny wymiar albo dalej niz 20 kratek
        public boolean near;      // ten sam wymiar, <= 20 kratek
        public double dist = -1;
        public Vec3d pos;
        public String dim = "";

        public String coords() {
            return (int) Math.floor(pos.x) + " " + (int) Math.floor(pos.y) + " " + (int) Math.floor(pos.z);
        }
    }

    public static Info info(MinecraftClient mc, String name) {
        Info i = new Info();
        i.online = Protocol.isOnline(mc, name);
        if (mc.player == null || mc.world == null) return i;

        AbstractClientPlayerEntity entity = null;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && p.getName().getString().equalsIgnoreCase(name)) { entity = p; break; }
        }

        String myDim = currentDim(mc);
        if (entity != null) {
            i.known = true;
            i.pos = entity.getPos();
            i.dim = myDim;
            i.sameDim = true;
        } else {
            Loc l = locations.get(name);
            if (l != null && System.currentTimeMillis() - l.time() < 30_000) {
                i.known = true;
                i.pos = new Vec3d(l.x(), l.y(), l.z());
                i.dim = l.dim();
                i.sameDim = l.dim().equals(myDim);
            }
        }
        if (i.known) {
            if (i.sameDim) {
                i.dist = i.pos.distanceTo(mc.player.getPos());
                i.far = i.dist > FAR_DISTANCE;
                i.near = !i.far;
            } else {
                i.far = true;
            }
        }
        return i;
    }
}
