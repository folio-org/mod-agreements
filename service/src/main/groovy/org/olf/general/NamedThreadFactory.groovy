package org.olf.general

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

public class NamedThreadFactory implements ThreadFactory {

    private static AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;
    private final boolean daemon;

    public NamedThreadFactory(String namePrefix, boolean daemon) {
        this.namePrefix = namePrefix;
        this.daemon = daemon;
    }

    public NamedThreadFactory(String namePrefix) {
        this(namePrefix, false);
    }

    public Thread newThread(Runnable runnable) {
        final Thread thread = new Thread(runnable, namePrefix + " thread-" + threadNumber.getAndIncrement());
        thread.setDaemon(daemon);
        return thread;
    }

}