package com.safedetect.agent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loaded with -javaagent so Lunar keeps its normal (non-Forge) mode. It never transforms a class: a
 * background thread only queues a task with Minecraft.addScheduledTask, and all game reads happen in that
 * task on the client thread.
 */
public final class Agent implements Runnable {
    private final Instrumentation instrumentation;
    private final AtomicBoolean queued = new AtomicBoolean();
    private Game game;
    private Detector detector;

    private Agent(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
    }

    public static void premain(String args, Instrumentation instrumentation) {
        start(instrumentation);
    }

    public static void agentmain(String args, Instrumentation instrumentation) {
        start(instrumentation);
    }

    private static void start(Instrumentation instrumentation) {
        try {
            File jar = new File(Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Log.open(new File(jar.getParentFile(), "safedetect-agent.log"));
        } catch (Throwable ignored) {
        }
        Log.info("Agent loaded on Java " + System.getProperty("java.version") + ".");
        Thread thread = new Thread(new Agent(instrumentation), "SafeDetect");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    @Override
    public void run() {
        try {
            if (!waitForGame()) {
                return;
            }
            final Runnable tick = new Runnable() {
                @Override
                public void run() {
                    queued.set(false);
                    try {
                        detector.frame();
                    } catch (Throwable thrown) {
                        Log.once("tick", thrown);
                    }
                }
            };
            while (true) {
                if (queued.compareAndSet(false, true)) {
                    try {
                        if (!game.schedule(tick)) {
                            queued.set(false);
                        }
                    } catch (Throwable thrown) {
                        queued.set(false);
                        Log.once("schedule", thrown);
                    }
                }
                Thread.sleep(detector.inWorld() ? 10L : 250L);
            }
        } catch (InterruptedException ignored) {
        } catch (Throwable thrown) {
            Log.once("agent thread", thrown);
        }
    }

    private boolean waitForGame() throws InterruptedException {
        while (game == null) {
            Thread.sleep(1000L);
            try {
                game = Game.find(instrumentation);
            } catch (Throwable thrown) {
                Log.once("find game", thrown);
            }
        }
        if (!game.missing().isEmpty()) {
            Log.info("Not found: " + game.missing());
        }
        if (!game.usable()) {
            Log.info("Required game members are missing; agent stopped without touching the game.");
            return false;
        }
        Log.info("Game classes resolved; checks are running.");
        detector = new Detector(game);
        Game.useGameLoader();
        return true;
    }
}
