package com.botmaker.plugin.toolkit;

import javafx.application.Platform;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Slow work off the JavaFX thread, its answer back on it.
 *
 * <p>Anything that walks a folder, shells out, opens a socket or decodes a picture freezes the whole
 * application when run in a click handler, and the freeze grows with what the user owns, so it rarely shows
 * while testing. The fix is always the same three steps — a daemon thread, the work, {@code Platform.runLater}
 * with the answer — and each copy gets one of them wrong: a thread that is not a daemon keeps Studio from
 * exiting, and a failure that escapes the worker leaves the window saying "Loading…" forever.
 *
 * <p>Each method returns the thread, already started, so a window that closes can interrupt work it no longer
 * wants. Work that should notice checks {@link Thread#isInterrupted()}; an answer for a window that has gone
 * is the caller's to drop.
 */
public final class Async {

    private static final Executor FX = Platform::runLater;

    private Async() {}

    /**
     * Runs {@code work} on a daemon thread named {@code name} and hands its result to {@code onDone} on the
     * JavaFX thread. If {@code work} throws, {@code onDone} is not called and {@code onFailure} gets one
     * sentence saying why, on the JavaFX thread — so the window can say so instead of waiting forever.
     */
    public static <T> Thread load(String name, Supplier<? extends T> work, Consumer<? super T> onDone,
                                  Consumer<String> onFailure) {
        return load(name, work, onDone, onFailure, FX);
    }

    /**
     * {@link #load(String, Supplier, Consumer, Consumer)} for work that does not fail, or whose failure there
     * is nothing to say about: a throw reaches the thread's uncaught-exception handler, and nothing else runs.
     */
    public static <T> Thread load(String name, Supplier<? extends T> work, Consumer<? super T> onDone) {
        return load(name, work, onDone, null, FX);
    }

    /**
     * Runs {@code work} on a daemon thread named {@code name}, then {@code then} on the JavaFX thread, or
     * nothing when {@code then} is {@code null} — for work with no answer, like opening a browser.
     */
    public static Thread run(String name, Runnable work, Runnable then) {
        return load(name, () -> {
            work.run();
            return null;
        }, done -> {
            if (then != null) then.run();
        }, null, FX);
    }

    /** {@link #load(String, Supplier, Consumer, Consumer)} with the hop back injected, so a test needs no FX. */
    static <T> Thread load(String name, Supplier<? extends T> work, Consumer<? super T> onDone,
                           Consumer<String> onFailure, Executor fx) {
        Thread thread = new Thread(() -> {
            T result;
            try {
                result = work.get();
            } catch (Throwable e) {
                // Throwable, not Exception: an OpenCV solve that runs out of memory is as much a failure the
                // window must hear about as a missing tool is.
                if (onFailure == null) {
                    if (e instanceof RuntimeException r) throw r;
                    if (e instanceof Error err) throw err;
                    throw new IllegalStateException(e);
                }
                String why = sentence(e);
                fx.execute(() -> onFailure.accept(why));
                return;
            }
            if (onDone != null) fx.execute(() -> onDone.accept(result));
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /** What went wrong, in the words the failure brought, or its kind when it brought none. */
    static String sentence(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
