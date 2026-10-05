package com.botmaker.plugin.toolkit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncTest {

    private final BlockingQueue<String> heard = new LinkedBlockingQueue<>();

    @Test
    void theResultArrivesThroughTheHopBack() throws Exception {
        Async.load("background-test", () -> "found 3", heard::add, why -> heard.add("failed: " + why), r -> {
            heard.add("hop");
            r.run();
        }).join(5000);
        assertEquals("hop", heard.poll(5, TimeUnit.SECONDS));
        assertEquals("found 3", heard.poll(5, TimeUnit.SECONDS));
    }

    @Test
    void aFailureArrivesAsASentenceAndTheResultCallbackDoesNotRun() throws Exception {
        Async.load("background-test", () -> {
            throw new IllegalStateException("adb is not installed");
        }, r -> heard.add("done"), why -> heard.add("failed: " + why), Runnable::run).join(5000);
        assertEquals("failed: adb is not installed", heard.poll(5, TimeUnit.SECONDS));
        assertTrue(heard.isEmpty());
    }

    @Test
    void theWorkRunsOnANamedDaemonThread() throws Exception {
        BlockingQueue<Thread> ran = new LinkedBlockingQueue<>();
        Thread worker = Async.load("background-test-named", () -> {
            ran.add(Thread.currentThread());
            return 1;
        }, r -> {}, null, Runnable::run);
        Thread on = ran.poll(5, TimeUnit.SECONDS);
        assertEquals(worker, on);
        assertEquals("background-test-named", on.getName());
        assertTrue(on.isDaemon());
        assertNotEquals(Thread.currentThread(), on);
    }

    @Test
    void aFailureWithNoMessageIsNamedByItsKind() {
        assertEquals("NullPointerException", Async.sentence(new NullPointerException()));
        assertEquals("UnsatisfiedLinkError", Async.sentence(new UnsatisfiedLinkError(" ")));
    }
}
