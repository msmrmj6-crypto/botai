package com.aicompanions.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Extremely small "run this in N server ticks" helper. Register
 * {@link #tick()} on ServerTickEvents.END_SERVER_TICK once in
 * AICompanionsMod; everything else just calls {@link #schedule}.
 * <p>
 * Runs tasks on whatever thread calls tick() - i.e. the server thread, if
 * you wire it up as suggested above - so it's safe to touch world state or
 * dispatch commands from inside a scheduled task.
 */
public final class TickScheduler {

    private record Scheduled(long dueAtTick, Runnable task) {
    }

    private static final ConcurrentLinkedQueue<Scheduled> QUEUE = new ConcurrentLinkedQueue<>();
    private static long currentTick = 0;

    private TickScheduler() {
    }

    public static void schedule(int delayTicks, Runnable task) {
        QUEUE.add(new Scheduled(currentTick + Math.max(delayTicks, 0), task));
    }

    public static void tick() {
        currentTick++;
        if (QUEUE.isEmpty()) {
            return;
        }
        List<Scheduled> due = new ArrayList<>();
        QUEUE.removeIf(s -> {
            if (s.dueAtTick() <= currentTick) {
                due.add(s);
                return true;
            }
            return false;
        });
        for (Scheduled s : due) {
            try {
                s.task().run();
            } catch (Exception e) {
                System.err.println("[AI Companions] Scheduled task failed: " + e.getMessage());
            }
        }
    }
}
