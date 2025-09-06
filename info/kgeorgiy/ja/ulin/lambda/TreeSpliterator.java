package info.kgeorgiy.ja.ulin.lambda;

import info.kgeorgiy.java.advanced.lambda.Trees;

import java.util.*;
import java.util.function.BiConsumer;

public class TreeSpliterator<T> extends AbstractTreeSpliterator<T, T> {
    public TreeSpliterator(Object root, BiConsumer<Stack<Object>, Queue<T>> tryAdvanceFunc) {
        super(root, tryAdvanceFunc, _ -> 1, IMMUTABLE | (root instanceof Trees.SizedBinary ? SIZED : 0));
    }
}
