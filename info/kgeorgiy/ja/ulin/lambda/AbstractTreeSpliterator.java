package info.kgeorgiy.ja.ulin.lambda;

import info.kgeorgiy.java.advanced.lambda.Trees;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class AbstractTreeSpliterator<T, R> implements Spliterator<T> {
    private final Stack<Object> root = new Stack<>();
    private final Queue<T> leafObj = new LinkedList<>();
    private final BiConsumer<Stack<Object>, Queue<T>> tryAdvanceFunc;
    private final Function<Trees.Leaf<R>, Integer> getLeafSize;
    private long remainingTime; // :NOTE: remainingTime -> Size
    private final int characteristics;

    public AbstractTreeSpliterator(Object root, BiConsumer<Stack<Object>, Queue<T>> tryAdvanceFunc, Function<Trees.Leaf<R>, Integer> getLeafSize, int characteristics) {
        this.root.push(root);
        this.tryAdvanceFunc = tryAdvanceFunc;
        this.getLeafSize = getLeafSize;
        this.characteristics = characteristics;
        this.remainingTime = (root instanceof Trees.SizedBinary<?> tree ? tree.size() : Long.MAX_VALUE);
    }

    @Override
    public boolean tryAdvance(Consumer<? super T> action) {
        if (!leafObj.isEmpty()) {
            T val = leafObj.poll();
            action.accept(val);
            return true;
        }

        if (root.isEmpty()) {
            return false;
        }

        tryAdvanceFunc.accept(root, leafObj);
        tryAdvance(action);

        return true;
    }

    @Override
    public Spliterator<T> trySplit() {
        if (root.isEmpty()) {
            return null;
        }

        Object tmp = root.pop();

        if (tmp instanceof Trees.SizedBinary<?> tree) {
            updateRemainingTime(tree.size());
        }

        return new AbstractTreeSpliterator<T, R>(tmp, tryAdvanceFunc, getLeafSize, characteristics);
    }

    @SuppressWarnings("unchecked")
    @Override
    public long estimateSize() {
        return (root.size() == 1 && root.peek() instanceof Trees.Leaf ? getLeafSize.apply((Trees.Leaf<R>) root.peek()) : remainingTime);
    }

    @Override
    public int characteristics() {
        return ORDERED | (estimateSize() != Long.MAX_VALUE ? SUBSIZED : 0) | characteristics;
    }

    private void updateRemainingTime(long upd) {
        if (remainingTime != Long.MAX_VALUE) {
            remainingTime -= upd;
        }
    }
}
