package info.kgeorgiy.ja.ulin.lambda;

import java.util.*;
import java.util.function.BiConsumer;

public class NestedTreeSpliterator<T> extends AbstractTreeSpliterator<T, List<T>> {
    public NestedTreeSpliterator(Object root, BiConsumer<Stack<Object>, Queue<T>> tryAdvanceFunc) {
        super(root, tryAdvanceFunc, leaf -> leaf.value().size(), 0);
    }
}
