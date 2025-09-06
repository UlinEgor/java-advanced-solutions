package info.kgeorgiy.ja.ulin.lambda;

import info.kgeorgiy.java.advanced.lambda.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.lang.Math.min;

public class Lambda implements AdvancedLambda {
    @SuppressWarnings("unchecked")
    private static  <R> void tryAdvance(Stack<Object> root, Consumer<Trees.Leaf<R>> updateLeaf) {
        Object tmp = root.pop();

        if (tmp instanceof Trees.SizedBinary.Branch<?> val) {
            root.push(val.right());
            root.push(val.left());
        } else if (tmp instanceof Trees.Binary.Branch<?>(Trees.Binary<?> left, Trees.Binary<?> right)) {
            root.push(right);
            root.push(left);

        } else if (tmp instanceof Trees.Nary.Node<?> val) {
            List<? extends Trees.Nary<?>> childrens = val.children();

            for (int i = childrens.size() - 1; i >= 0; --i) {
                root.push(childrens.get(i));
            }
        } else if (tmp instanceof Trees.Leaf<?> leaf) {
            updateLeaf.accept((Trees.Leaf<R>)leaf);
        }
    }

    private static <T> void tryAdvanceCommon(Stack<Object> root, Queue<T> leafValues) {
        Lambda.<T>tryAdvance(root, leaf -> leafValues.add(leaf.value()));
    }

    private static <T> void tryAdvanceNested(Stack<Object> root, Queue<T> leafValues) {
        Lambda.<List<T>>tryAdvance(root, leaf -> leafValues.addAll(leaf.value()));
    }

    @Override
    public <T> Spliterator<T> binaryTreeSpliterator(Trees.Binary<T> binary) {
        return new TreeSpliterator<>(binary, Lambda::tryAdvanceCommon);
    }

    @Override
    public <T> Spliterator<T> sizedBinaryTreeSpliterator(Trees.SizedBinary<T> sizedBinary) {
        return new TreeSpliterator<>(sizedBinary, Lambda::tryAdvanceCommon);
    }

    @Override
    public <T> Spliterator<T> naryTreeSpliterator(Trees.Nary<T> nary) {
        return new TreeSpliterator<>(nary, Lambda::tryAdvanceCommon);
    }

    @Override
    public <T> Collector<T, ?, Optional<T>> first() {
        // :NOTE: return Collectors.reducing((a, b) -> a);
        return Collectors.collectingAndThen(head(1),
                list -> list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst()));
    }

    @Override
    public <T> Collector<T, ?, Optional<T>> last() {
        return Collectors.collectingAndThen(tail(1),
                list -> list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst()));
    }

    @Override
    public <T> Collector<T, ?, Optional<T>> middle() {
        class Info {
            public final Queue<T> list = new LinkedList<>();// :NOTE: ArrayDeque
            public boolean needErase = false;
        }

        return Collector.of(
                Info::new,
                (info, val) -> {
                    if (info.needErase) {
                        info.list.poll();
                    }

                    info.needErase = !info.needErase;
                    info.list.add(val);
                },
                (left, _) -> left, // :NOTE: кинуть исключение?
                info ->  Optional.ofNullable(info.list.poll())
        );
    }

    private String updateString(String prefix, int strLength, Function<Integer, Character> getIndex) {
        StringBuilder commonPrefix = new StringBuilder();
        for (int i = 0; i < min(prefix.length(), strLength); ++i) {
            if (prefix.charAt(i) == getIndex.apply(i)) {
                commonPrefix.append(prefix.charAt(i));
            } else break;
        }
        return commonPrefix.toString();
    }

    @Override
    public Collector<CharSequence, ?, String> commonPrefix() {
        class Info {
            public String prefix = null;
        }

        // :NOTE: Collectors.reducing()
        return Collector.of(
                Info::new,
                (info, str) -> {
                    if (info.prefix == null) {
                        info.prefix = str.toString();
                    } else {
                        info.prefix = updateString(info.prefix, str.length(), str::charAt);
                    }
                },
                (left, _) -> left,
                info -> info.prefix == null ? "" : info.prefix
        );
    }

    @Override
    public Collector<CharSequence, ?, String> commonSuffix() {
        class Info {
            public String prefix = null;
        }

        return Collector.of(
                Info::new,
                (info, str) -> {
                    if (info.prefix == null) {
                        info.prefix = new StringBuilder(str.toString()).reverse().toString();
                    } else {
                        info.prefix = updateString(info.prefix, str.length(), i -> str.charAt(str.length() - i - 1));
                    }
                },
                (left, _) -> left,
                info -> info.prefix == null ? "" : new StringBuilder(info.prefix).reverse().toString()
        );
    }

    @Override
    public <T> Spliterator<T> nestedBinaryTreeSpliterator(Trees.Binary<List<T>> binary) {
        return new NestedTreeSpliterator<T>(binary, Lambda::tryAdvanceNested);
    }

    @Override
    public <T> Spliterator<T> nestedSizedBinaryTreeSpliterator(Trees.SizedBinary<List<T>> sizedBinary) {
        return new NestedTreeSpliterator<T>(sizedBinary, Lambda::tryAdvanceNested);
    }

    @Override
    public <T> Spliterator<T> nestedNaryTreeSpliterator(Trees.Nary<List<T>> nary) {
        return new NestedTreeSpliterator<T>(nary, Lambda::tryAdvanceNested);
    }

    @Override
    public <T> Collector<T, ?, List<T>> head(int i) {
        return Collector.of(
                ArrayList<T>::new,
                (list, val) -> {
                    if (list.size() < i) {
                        list.add(val);
                    }
                },
                (left, _) -> left,
                list -> list
        );
    }

    @Override
    public <T> Collector<T, ?, List<T>> tail(int i) {
        return Collector.of(
                LinkedList<T>::new,
                (list, val) -> {
                    list.add(val);

                    if (list.size() >= i + 1) {
                        list.poll();
                    }
                },
                (left, _) -> left,
                list -> list
        );
    }

    @Override
    public <T> Collector<T, ?, Optional<T>> kth(int i) {
        class Info {
            public Optional<T> val = Optional.empty(); // :NOTE: Optional<T> -> T
            public int pos = i;
        }

        return Collector.of(
                Info::new,
                (info, val) -> {
                    if (info.pos == 0) {
                        info.val = Optional.of(val);
                    }
                    info.pos--;
                },
                (left, _) -> left,
                info -> info.val
        );
    }

    @Override
    public <T> Collector<T, ?, List<T>> distinctBy(Function<? super T, ?> mapper) {
        class Pair {
            public final Set<Object> was = new HashSet<>();
            public final List<T> list = new ArrayList<>();
        }

        return Collector.of(
                Pair::new,
                (pair, val) -> {
                    if (pair.was.add(mapper.apply(val))) {
                        pair.list.add(val);
                    }
                },
                (left, _) -> left, // :NOTE:
                pair -> pair.list
        );
    }

    @Override
    public <T> Collector<T, ?, OptionalLong> minIndex(Comparator<? super T> comparator) {
        return indexCollector(comparator, true);
    }

    @Override
    public <T> Collector<T, ?, OptionalLong> maxIndex(Comparator<? super T> comparator) {
        return indexCollector(comparator, false);
    }

    private static <T> Collector<T, ?, OptionalLong> indexCollector(Comparator<? super T> comparator, boolean findMin) {
        class Info {
            long bestIndex = -1;
            long currentIndex = 0;
            T bestElement = null;
        }

        return Collector.of(
                Info::new,
                (info, val) -> {
                    if (info.bestIndex == -1 || (findMin ? comparator.compare(val, info.bestElement) < 0
                            : comparator.compare(val, info.bestElement) > 0)) {
                        info.bestIndex = info.currentIndex;
                        info.bestElement = val;
                    }

                    info.currentIndex++;
                },
                (left, _) -> left, // :NOTE:
                info -> info.bestIndex == -1 ? OptionalLong.empty() : OptionalLong.of(info.bestIndex)
        );
    }
}
