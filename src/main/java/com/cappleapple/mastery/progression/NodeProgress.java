package com.cappleapple.mastery.progression;

/** Persisted purchase chronology is independent of the client's graph coordinates. */
public final class NodeProgress {
    private int rank;
    private long purchaseOrder;
    private long unlockOrder;
    private boolean toggled = true;

    public int rank() { return rank; }
    public void rank(int value) { rank = Math.max(0, value); }
    public long purchaseOrder() { return purchaseOrder; }
    public void purchaseOrder(long value) { purchaseOrder = Math.max(0, value); }
    public long unlockOrder() { return unlockOrder; }
    public void unlockOrder(long value) { unlockOrder = Math.max(0, value); }
    public boolean toggled() { return toggled; }
    public void toggled(boolean value) { toggled = value; }
}
