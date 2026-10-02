package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

/** Identifica al último worker de una etapa para propagar las píldoras. */
public final class StageBarrier {
    private final int workerCount;
    private int finishedWorkers;

    public StageBarrier(int workerCount) {
        this.workerCount = workerCount;
    }

    public synchronized boolean workerFinished() {
        finishedWorkers++;
        return finishedWorkers == workerCount;
    }
}
