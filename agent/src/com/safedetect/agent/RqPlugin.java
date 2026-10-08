package com.safedetect.agent;

/** Manual requeue of the last QuickPlay mode. Does not auto-leave. */
final class RqPlugin extends PluginPack.Base {
    RqPlugin() {
        super("rq", "Requeue",
                "Sends /play for the last mode you queued with /sd play. Manual only — not auto-requeue.",
                "", "/sd rq");
    }

    @Override
    public boolean command(String[] parts) {
        String play = api.config("play.last");
        String name = api.config("play.lastName");
        if (play.isEmpty()) {
            play = "bedwars_eight_two";
            name = "Doubles";
        }
        api.send("/play " + play);
        api.chat("\u00a77Queueing \u00a7f" + (name.isEmpty() ? play : name) + "\u00a77.");
        return true;
    }
}
