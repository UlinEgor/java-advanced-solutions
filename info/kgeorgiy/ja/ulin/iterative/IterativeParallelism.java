package info.kgeorgiy.ja.ulin.iterative;

import info.kgeorgiy.java.advanced.iterative.AdvancedIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Math.min;

/**
 * Class for parallels computing.
 * <p>
 * Have some methods for parallels computing.
 * </p>
 */
public class IterativeParallelism implements AdvancedIP {
    private final ParallelMapper mapper;

    /**
     * Creates an {@code IterativeParallelism} class, that run tasks with internal threads.
     */
    public IterativeParallelism() {
        mapper = null;
    }

    /**
     * Creates an {@code IterativeParallelism} class, that run tasks with {@code ParallelMapper}.
     *
     * @param mapper mapper that need to use to run tasks.
     */
    public IterativeParallelism(ParallelMapper mapper) {
        this.mapper = mapper;
    }

    private <U> Stream<U> calculateSublist(
            Pair<Integer, Integer> pos,
            Function<Integer, Optional<U>> calculate,
            Function<Stream<U>, Stream<U>> postEval) {
        List<Optional<U>> builder = new ArrayList<>();

        for (int j = pos.first(); j < pos.second(); ++j) {
            Optional<U> tmp = calculate.apply(j);
            builder.add(tmp);
        }

        return postEval.apply(builder.stream().flatMap(Optional::stream));
    }

    private <U> Stream<U> evaluate(
            int threads,
            Function<Integer, Pair<Integer, Integer>> getPos,
            Function<Integer, Optional<U>> calculate,
            Function<Stream<U>, Stream<U>> postEval
    ) throws InterruptedException {
        Thread[] t = new Thread[threads];

        List<Stream<U>> ans;

        if (Objects.nonNull(mapper)) {
            List<Pair<Integer, Integer>> ranges = IntStream.range(0, threads).boxed().map(getPos).collect(Collectors.toList());

            ans = mapper.map(pos -> calculateSublist(pos, calculate, postEval), ranges);
        } else {
            ans = new ArrayList<>(Collections.nCopies(threads, null));

            for (int i = 0; i < threads; ++i) {
                Pair<Integer, Integer> pos = getPos.apply(i);
                final int finI = i;

                t[i] = new Thread(() -> ans.set(finI, calculateSublist(pos, calculate, postEval)));
                t[i].start();
            }

            joinThreads(t);
        }

        return ans.stream().reduce(Stream.of(), Stream::concat);
    }

    private static void joinThreads(Thread[] t) throws InterruptedException {
        int threads = t.length;

        InterruptedException error = null;
        for (int i = 0; i < threads; ++i) {
            while (true) {
                try {
                    t[i].join();
                    break;
                } catch (InterruptedException e) {
                    if (Objects.nonNull(error)) {
                        error.addSuppressed(e);
                    } else {
                        for (int j = i; j < threads; ++j) {
                            t[i].interrupt();
                        }
                        error = e;
                    }
                }
            }
        }

        if (Objects.nonNull(error)) {
            throw error;
        }
    }

    private <U> Stream<U> eval(
            int threads,
            int size,
            Function<Integer, Optional<U>> func,
            Function<Stream<U>, Stream<U>> postEval
    ) throws InterruptedException {
        if (size < threads) {
            threads = size;
        }
        final int threadsFinal = threads;

        return postEval.apply(evaluate(threadsFinal,
                i -> {
                    if (i == -1) {
                        return new Pair<>(0, size);
                    }

                    int blockSize = (size + threadsFinal - 1) / threadsFinal;
                    return new Pair<>(blockSize * i, min(blockSize * (i + 1), size));
                },
                func, postEval)
        );
    }

    private record Pair<T, U>(T first, U second) {
    }

    @Override
    public <T> int argMax(int threads, List<T> list, Comparator<? super T> comparator) throws InterruptedException {
        Comparator<Pair<T, Integer>> cmp = Comparator
                .comparing((Pair<T, Integer> p) -> p.first(), comparator)
                .thenComparing(Pair::second, Comparator.reverseOrder());

        return eval(threads, list.size(), (i) -> Optional.of(new Pair<>(list.get(i), i)), stream -> stream.max(cmp).stream()).findFirst().orElse(new Pair<>(null, -1)).second();
    }

    @Override
    public <T> int argMin(int threads, List<T> list, Comparator<? super T> comparator) throws InterruptedException {
        return argMax(threads, list, comparator.reversed());
    }

    @Override
    public <T> int indexOf(int threads, List<T> list, Predicate<? super T> predicate) throws InterruptedException {
        return eval(threads, list.size(), (i) -> predicate.test(list.get(i)) ? Optional.of(i) : Optional.empty(), a -> a.findFirst().stream()).findFirst().orElse(-1);
    }

    @Override
    public <T> int lastIndexOf(int threads, List<T> list, Predicate<? super T> predicate) throws InterruptedException {
        int ans = indexOf(threads, list.reversed(), predicate);
        if (ans == -1) {
            return ans;
        } else {
            return list.size() - ans - 1;
        }
    }

    @Override
    public <T> long sumIndices(int threads, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return eval(threads, list.size(), (i) -> predicate.test(list.get(i)) ? Optional.of((long) i) : Optional.empty(), a -> Stream.of(a.reduce(0L, Long::sum))).findFirst().orElse(0L);
    }

    @Override
    public <T> int[] indices(int threads, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return eval(threads, list.size(), (i) -> predicate.test(list.get(i)) ? Optional.of(i) : Optional.empty(), a -> a).mapToInt(Integer::intValue).toArray();
    }

    @Override
    public <T> List<T> filter(int threads, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return eval(threads, list.size(), (i) -> predicate.test(list.get(i)) ? Optional.of(list.get(i)) : Optional.empty(), a -> a).collect(Collectors.toList());
    }

    @Override
    public <T, U> List<U> map(int threads, List<? extends T> list, Function<? super T, ? extends U> function) throws InterruptedException {
        if (!Objects.nonNull(function)) {
            throw new NullPointerException("Function is null");
        }

        return eval(threads, list.size(), (i) -> Optional.of(function.apply(list.get(i))), a -> a).collect(Collectors.toList());
    }

    @Override
    public <T> T reduce(int threads, List<T> list, T neutral, BinaryOperator<T> binaryOperator) throws InterruptedException {
        return eval(threads, list.size(), (i) -> Optional.of(list.get(i)), a -> a).reduce(neutral, binaryOperator);
    }

    @Override
    public <T, R> R mapReduce(int threads, List<T> list, Function<T, R> function, R neutral, BinaryOperator<R> binaryOperator) throws InterruptedException {
        return reduce(threads, map(threads, list, function), neutral, binaryOperator);
    }
}
