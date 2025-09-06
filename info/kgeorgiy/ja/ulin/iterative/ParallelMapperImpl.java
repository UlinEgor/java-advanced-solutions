package info.kgeorgiy.ja.ulin.iterative;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;

public class ParallelMapperImpl implements ParallelMapper {
    private final Queue<Runnable> q = new ArrayDeque<>();
    private final Thread[] t;
    private boolean isClosed = false;

    /**
     * Create an instance of {@code ParallelMapperImpl} to map in parallels.
     *
     * @param threads number of threads to run map tasks.
     */
    public ParallelMapperImpl(int threads) {
        if (threads <= 0) {
            throw new IllegalArgumentException("Number of threads must be more than 0");
        }

        t = new Thread[threads];
        for (int i = 0; i < threads; ++i) {
            Thread thread = new Thread(() -> {
                try {
                    eval();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            t[i] = thread;

            t[i].start();
        }
    }

    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> function, List<? extends T> list) throws InterruptedException {
        if (!Objects.nonNull(function)) {
            throw new IllegalStateException("Function must be not null");
        }
        if (isClosed) throw new IllegalStateException("Mapper is closed");
        List<R> ans = new ArrayList<>(Collections.nCopies(list.size(), null));

        Counter counter = new Counter(list.size());
        ErrorHandling error = new ErrorHandling();

        for (int i = 0; i < list.size(); ++i) {
            final int finI = i;
            synchronized (q) {
                q.add(() -> {
                    try {
                        ans.set(finI, function.apply(list.get(finI)));
                    } catch (RuntimeException e) {
                        error.addSuppressed(e);
                    }

                    counter.decrement();
                });
                q.notify();
            }
        }

        counter.waitEnd();
        if (isClosed) throw new IllegalStateException("Mapper is closed");
        error.checkErrors();

        return ans;
    }

    private void eval() throws InterruptedException {
        try {
            while (true) {
                Runnable task;
                synchronized (q) {
                    while (q.isEmpty()) {
                        q.wait();
                    }

                    task = q.poll();
                }

                if (isClosed) {
                    break;
                }

                task.run();
            }
        } catch (InterruptedException _) {

        }
    }

    @Override
    public void close() {
        if (isClosed) {
            return;
        }
        isClosed = true;
        interruptThreads(t);
    }

    private static void interruptThreads(Thread[] t) {
        for (Thread thread : t) {
            thread.interrupt();
        }
    }

    private static class Counter {
        private int count;

        Counter(int start) {
            count = start;
        }

        public synchronized void decrement() {
            --count;

            if (count == 0) {
                notify();
            }
        }

        public synchronized void waitEnd() throws InterruptedException {
            while (count > 0) {
                wait();
            }

        }
    }

    private static class ErrorHandling {
        private RuntimeException error = null;

        public synchronized void addSuppressed(RuntimeException e) {
            if (Objects.nonNull(error)) {
                error.addSuppressed(e);
            } else {
                error = e;
            }
        }

        public synchronized void checkErrors() {
            if (Objects.nonNull(error)) {
                throw error;
            }
        }
    }
}
