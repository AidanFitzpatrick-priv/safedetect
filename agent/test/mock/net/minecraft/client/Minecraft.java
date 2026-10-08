package net.minecraft.client;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

public class Minecraft {
    private static Minecraft theMinecraft;
    public EntityPlayerSP thePlayer;
    public WorldClient theWorld;
    public final File mcDataDir;
    public final GuiIngame ingameGUI = new GuiIngame();
    public Object currentScreen;
    private final Queue<FutureTask<?>> scheduledTasks = new ArrayDeque<FutureTask<?>>();
    private final Thread mcThread = Thread.currentThread();
    public int tasksRun;

    public Minecraft(File dataDir) {
        mcDataDir = dataDir;
        theMinecraft = this;
    }

    public static Minecraft getMinecraft() {
        return theMinecraft;
    }

    public <V> Object addScheduledTask(Callable<V> callable) {
        if (isCallingFromMinecraftThread()) {
            throw new IllegalStateException("called from client thread");
        }
        FutureTask<V> task = new FutureTask<V>(callable);
        synchronized (scheduledTasks) {
            scheduledTasks.add(task);
        }
        return task;
    }

    public Object addScheduledTask(Runnable runnable) {
        return addScheduledTask(Executors.callable(runnable));
    }

    public boolean isCallingFromMinecraftThread() {
        return Thread.currentThread() == mcThread;
    }

    public NetHandlerPlayClient getNetHandler() {
        return null;
    }

    public GuiChat openChat() {
        GuiChat chat = new GuiChat();
        currentScreen = chat;
        return chat;
    }

    public void runScheduledTasks() {
        synchronized (scheduledTasks) {
            while (!scheduledTasks.isEmpty()) {
                scheduledTasks.poll().run();
                tasksRun++;
            }
        }
    }
}
