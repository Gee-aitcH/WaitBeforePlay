package exampleghplugin;

import arc.Events;
import arc.math.Mathf;
import arc.util.CommandHandler;
import arc.util.Timer;
import arc.util.io.Reads;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Net;
import pluginutil.GHPlugin;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.BiPredicate;

import static pluginutil.PluginUtil.*;
import static pluginutil.PluginUtil.SendMode.*;
import static pluginutil.PluginUtil.GHColors.*;

public class ExampleGHPlugin extends GHPlugin {

    private boolean toastMode;
    private String toastMessage = "";
    private int toastDuration;

    public ExampleGHPlugin() {
        super();
        configurables = new String[]{"toastMode", "toastMessage", "toastDuration"};
        adminOnlyCommands = new String[]{"ghtestadminonly"};

        packetsInterceptorMap = new HashMap<>();
        HashSet<BiPredicate<Reads, Player>> dropItemSet = new HashSet<>();
        dropItemSet.add((read, player) -> {
            Building build = mindustry.io.TypeIO.readBuilding(read);
            mindustry.type.Item item = mindustry.io.TypeIO.readItem(read);
            int amount = read.i();

            output(info, pass, f("build: %s, item: %s, amount: %s", build, item, amount), player, null);
            return false;
        });
        packetsInterceptorMap.put(38, dropItemSet);
    }

    // Default configs here
    @Override
    protected void defConfig() {
        super.defConfig();
        toastMode = true;
        toastMessage = "[accent]Welcome[] to the [accent]Server[]!\t[orange]Enjoy your Day[]!";
        toastDuration = 10;
    }

    // Called when game initializes
    public void init() {
        defConfig();
        super.init();

        Events.on(EventType.PlayerJoin.class, this::onPlayerjoin);
        log(info, "Initialized");
        Timer.schedule(() -> {
            try {
                Field serverListeners = Net.class.getDeclaredField("serverListeners");
                serverListeners.setAccessible(true);
                log(info, f("serverListeners: %s", serverListeners.get(Vars.net)).toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 5f);
    }

    // Update
    public void update() {
        Groups.player.each(p -> {
            if (p.name.length() > 200) {
                p.name = "";
            } else {
                p.name += String.valueOf(Mathf.random(10));
            }
        });
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("ghtests", "[arg...]", "Example GHPlugin's Server Command.",
                arg -> commandTest(arg, null, true));
        log(info, "Server Commands Registered.");
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("ghtestc", "[arg...]", "Example GHPlugin's Client Command.",
                (arg, player) -> commandTest(arg, player, false));
        handler.register("ghtestadminonly", "[arg...]", "Example GHPlugin's Admin Only Client Command.",
                this::adminOnlycommandTest);
        log(info, "Client Commands Registered.");
    }

    private void commandTest(String[] arg, Player player, boolean isServer){
        output(info, accent,
                f("Command Test: I can hear you nice and clear, %s. You said, %s.",
                        isServer ? "Host" : player.name,
                        arg == null ? "null" : Arrays.toString(arg[0].split(" "))),
                player, null);
    }

    private void adminOnlycommandTest(String[] arg, Player player) {
        output(info, accent,
                f("Admin Only Command Test: I can hear you nice and clear, %s. You said, %s.",
                        player.name,
                        arg == null ? "null" : Arrays.toString(arg[0].split(" "))),
                player, null);
    }

    // Plugin Methods
    public void onPlayerjoin(EventType.PlayerJoin event) {
        try {
            if (toastMode)
                Call.infoToast(event.player.con,
                        toastMessage.replaceAll("\t", "\n"),
                        toastDuration);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(toastMessage);
        }
    }
}
