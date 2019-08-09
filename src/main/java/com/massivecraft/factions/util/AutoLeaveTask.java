package com.massivecraft.factions.util;

import com.massivecraft.factions.FactionsPlugin;

public class AutoLeaveTask implements Runnable {

    private static AutoLeaveProcessTask task;
    double rate;

    public AutoLeaveTask() {
        this.rate = FactionsPlugin.getInstance().conf().factions().getAutoLeaveRoutineRunsEveryXMinutes();
    }

    public synchronized void run() {
        if (task != null && !task.isFinished()) {
            return;
        }

        task = new AutoLeaveProcessTask();
        task.runTaskTimer(FactionsPlugin.p, 1, 1);

        // maybe setting has been changed? if so, restart this task at new rate
        if (this.rate != FactionsPlugin.getInstance().conf().factions().getAutoLeaveRoutineRunsEveryXMinutes()) {
            FactionsPlugin.getInstance().startAutoLeaveTask(true);
        }
    }
}
