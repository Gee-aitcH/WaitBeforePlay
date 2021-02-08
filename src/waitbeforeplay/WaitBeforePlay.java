package waitbeforeplay;

import arc.Events;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Time;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import pluginutil.GHPlugin;

import java.util.Arrays;
import java.util.HashSet;

import static pluginutil.PluginUtil.f;
import static pluginutil.PluginUtil.GHColors.*;

@SuppressWarnings("unused")
public class WaitBeforePlay extends GHPlugin {

    private HashSet<Player> newPlayers;
    private float timeSinceLastSave;

    public WaitBeforePlay() {
        super();
        PLUGIN = this.getClass().getSimpleName();
    }

    // Called when game initializes
    public void init() {
        defConfig();
        super.init();
        Events.on(EventType.PlayerJoin.class, e -> onPlayerJoin(e.player));
        log("Initialized\n");
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("wbp", "<reload/save>", "Wait Before Playing Plugin's Command.", (args) -> {
            switch (args[0]){
                case "reload" -> {
                    read();
                    Groups.player.each(this::onPlayerJoin);
                    timeSinceLastSave = 0;
                    log("Reloaded");
                }

                case "save" -> {
                    write();
                    log("Saved");
                }

                default -> log("Only reload and save are available at the moment.");
            }
        });
    }

    private void onPlayerJoin(Player player) {
        if (player == null)
            return;

        if (player.admin) {
            cfg().oldPlayers.addAll(Arrays.asList(player.usid(), player.con.address));
            return;
        }

        if (cfg().oldPlayers.contains(player.usid()) && cfg().oldPlayers.contains(player.con.address))
            return;

        PlayTime playtime;
        if ((playtime = getPlayTime(cfg().newPlayTimes, player)) == null) {
            playtime = new PlayTime(player.usid(), player.con.address);
            cfg().newPlayTimes.add(playtime);
        }

        if (newPlayers.add(player)){
            msg(pass, f("Welcome, %s%s. Since you are a new player, you are only allowed to move and chat for now. " +
                            "But no worries! You will be able to do much more after being here for %s more minutes from now!",
                    player.name, pass, (int) Math.ceil(cfg().minsBeforePlaying - playtime.totalPlayTime / 60 / 60)), player);
            Team emptyTeam = Seq.with(Team.all).find(t -> t.cores().size == 0);
            newPlayers.forEach(p -> p.team(emptyTeam));
        }
    }

//    protected boolean onDisconnect(Object obj) {
//        Object[] objs;
//        NetConnection con;
////        Packets.Disconnect packet;
//        Player player;
//        try {
//            objs = (Object[]) obj;
//            con = (NetConnection) objs[0];
////            packet = (Packets.Disconnect) objs[1];
//            player = con.player;
//        } catch (Exception e){
//            log(info, f("Malformed packet data: %s", obj));
//            return false;
//        }
//
//        newPlayers.remove(player);
//        return false;
//    }
//
//    protected boolean onInvokePacket(Object obj) {
//        Object[] objs;
//        NetConnection con;
//        Packets.InvokePacket packet;
//        Reads read;
//        int type;
//        Player player;
//        try {
//            objs = (Object[]) obj;
//            con = (NetConnection) objs[0];
//            packet = (Packets.InvokePacket) objs[1];
//            read = packet.reader();
//            type = packet.type;
//            player = con.player;
//        } catch (Exception e) {
//            log(f("Malformed packet data: %s", obj));
//            return false;
//        }
//
//        System.out.println(type);
//
//        boolean overwrite = switch (type) {
//            case 0, 30, 44 -> false;
//
//            case 10 -> {
//                onPlayerJoin(player);
//                yield false;
//            }
//
//            case 8 -> {
//                if (!newPlayers.contains(player)) yield false;
//                try {
//                    int snapshotID = read.i();
//                    int unitID = read.i();
//                    boolean dead = read.bool();
//                    float x = read.f();
//                    float y = read.f();
//                    float pointerX = read.f();
//                    float pointerY = read.f();
//                    float rotation = read.f();
//                    float baseRotation = read.f();
//                    float xVelocity = read.f();
//                    float yVelocity = read.f();
//                    mindustry.world.Tile mining = mindustry.io.TypeIO.readTile(read);
//                    boolean boosting = read.bool();
//                    boolean shooting = read.bool();
//                    boolean chatting = read.bool();
//                    boolean building = read.bool();
//                    mindustry.entities.units.BuildPlan[] requests = mindustry.io.TypeIO.readRequests(read);
//                    float viewX = read.f();
//                    float viewY = read.f();
//                    float viewWidth = read.f();
//                    float viewHeight = read.f();
//                    mindustry.core.NetServer.clientSnapshot(player, snapshotID, unitID, dead, x, y,
//                            pointerX, pointerY, rotation, baseRotation, xVelocity, yVelocity,
//                            null, boosting, false, chatting, false, new BuildPlan[0],
//                            viewX, viewY, viewWidth, viewHeight);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                yield true;
//            }
//
//            default -> !newPlayers.contains(player);
//        };
//
//        return overwrite;
//    }

    public void update(){
        newPlayers.removeIf(newPlayer -> {
            if (newPlayer.con.hasDisconnected)
                return true;

            PlayTime playtime = getPlayTime(cfg().newPlayTimes, newPlayer);
            if(playtime == null) {
                playtime = new PlayTime(newPlayer.usid(), newPlayer.con.address);
                cfg().newPlayTimes.add(playtime);
            }

            if(playtime.totalPlayTime > cfg().minsBeforePlaying * 60 * 60){
                cfg().newPlayTimes.remove(playtime);
                cfg().oldPlayers.addAll(Arrays.asList(playtime.keys));
                msg(pass, f("Welcome, %s%s! You are no longer a new player! Feel free to build and help out others!",
                        newPlayer.name, pass), newPlayer);
                newPlayer.team(Team.sharded);
                return true;
            }

            playtime.totalPlayTime += Time.delta;
            return false;
        });

        if (timeSinceLastSave > cfg().saveInterval * 60 * 60){
            write(true);
            timeSinceLastSave = 0;
        }else
            timeSinceLastSave += Time.delta;
    }

    private PlayTime getPlayTime(HashSet<PlayTime> playTimes, Player player) {
        for (PlayTime pt : playTimes)
            for (String key : pt.keys)
                if (player.usid().equals(key) || player.con.address.equals(key))
                    return pt;
        return null;
    }

    // Default configs here
    @Override
    protected void defConfig() {
        newPlayers = new HashSet<>();
        timeSinceLastSave = 0;
        cfg = new WaitBeforePlayingConfig();
    }

    protected WaitBeforePlayingConfig cfg(){
        return (WaitBeforePlayingConfig) cfg;
    }

    public static class WaitBeforePlayingConfig extends GHPluginConfig {

        protected long saveInterval;
        protected long minsBeforePlaying;
        protected HashSet<PlayTime> newPlayTimes;
        protected HashSet<String> oldPlayers;

        public WaitBeforePlayingConfig() {
            this.reset();
        }

        public void reset() {
            saveInterval = 1;
            minsBeforePlaying = 15;
            newPlayTimes = new HashSet<>();
            oldPlayers = new HashSet<>();
        }
    }

    private static class PlayTime{
        String[] keys;
        float totalPlayTime;

        public PlayTime(String... keys) {
            this.keys = keys;
            totalPlayTime = 0;
        }
    }
}
