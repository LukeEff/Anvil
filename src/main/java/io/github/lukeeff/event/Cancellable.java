package io.github.lukeeff.event;

public interface Cancellable {

    /**
     * Checks if an event is canceled.
     *
     * @return true if it is canceled.
     */
    boolean isCanceled();

    /**
     * Sets true or false that an event will be canceled.
     *
     * @param cancel true if it will be canceled.
     */
    void setCanceled(boolean cancel);

}
