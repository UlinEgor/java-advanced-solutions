package info.kgeorgiy.ja.ulin.arrayset;

import info.kgeorgiy.java.advanced.arrayset.AdvancedSet;

import java.util.*;

public class ArraySet<E> extends AbstractSet<E> implements AdvancedSet<E> {
    private final List<E> elements;
    private final Comparator<? super E> comparator;

    @SuppressWarnings("unchecked")
    public ArraySet(final Collection<? extends E> elements, final Comparator<? super E> comparator) {
        this.comparator = comparator;
        if (elements instanceof SortedSet<?> && Objects.equals(((SortedSet<? extends E>) elements).comparator(), comparator)) {
            this.elements = (List<E>) elements.stream().toList();
        } else {
            TreeSet<E> tree = new TreeSet<>(comparator);
            tree.addAll(elements);

            this.elements = Collections.unmodifiableList(new ArrayList<>(tree));
        }
    }

    public ArraySet(final Collection<? extends E> elements) {
        this(elements, null);
    }

    public ArraySet(final Comparator<? super E> comparator) {
        this(List.of(), comparator);
    }

    public ArraySet() {
        this(List.of(), null);
    }

    public ArraySet(final SortedSet<E> set, final Comparator<? super E> comparator) {
        this((Collection<? extends E>) set, comparator);
    }

    public ArraySet(SortedSet<E> set) {
        comparator = set.comparator();
        elements = set.stream().toList();
    }

    private ArraySet(final List<E> elements, int idxFrom, int idxTo, final Comparator<? super E> comparator) {
        this.elements = elements.subList(idxFrom, idxTo);
        this.comparator = comparator;
    }

    private ArraySet(final List<E> elements, final Comparator<? super E> comparator) {
        this(elements, 0, elements.size(), comparator);
    }

    private E commonSearch(E element, int firstAdd, int secondAdd) {
        int idx = Collections.binarySearch(elements, element, comparator);
        if (idx < 0) idx = -idx + firstAdd;
        else idx = idx + secondAdd;

        return (idx < 0 || elements.isEmpty() || idx >= elements.size() ? null : elements.get(idx));
    }

    @Override
    public E lower(E element) {
        return commonSearch(element, -2, -1);
    }

    @Override
    public E floor(E element) {
        return commonSearch(element, -2, 0);
    }

    @Override
    public E ceiling(E element) {
        return commonSearch(element, -1, 0);
    }

    @Override
    public E higher(E element) {
        return commonSearch(element, -1, 1);
    }

    @Override
    public E pollFirst() {
        throw new UnsupportedOperationException();
    }

    @Override
    public E pollLast() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<E> iterator() {
        return elements.iterator();
    }

    @Override
    public NavigableSet<E> descendingSet() {
        return new ArraySet<>(elements.reversed(), Collections.reverseOrder(comparator));
    }

    @Override
    public Iterator<E> descendingIterator() {
        return descendingSet().iterator();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(Object e) {
        return Collections.binarySearch(elements, (E)e, comparator) >= 0;
    }

    @Override
    public int size() {
        return elements.size();
    }

    private AdvancedSet<E> subSetImpl(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
        int idxFrom = Collections.binarySearch(elements, fromElement, comparator);
        int idxTo = Collections.binarySearch(elements, toElement, comparator);

        if (idxFrom >= 0 && !fromInclusive) {
            ++idxFrom;
        } else if (idxFrom < 0 ) {
            idxFrom = -idxFrom - 1;
        }

        if (idxTo >= 0 && toInclusive) {
            ++idxTo;
        } else if (idxTo < 0) {
            idxTo = -idxTo - 1;
        }

        return idxFrom > idxTo ? new ArraySet<>(comparator) : new ArraySet<>(elements, idxFrom, idxTo, comparator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AdvancedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
        if ((comparator != null && comparator.compare(fromElement, toElement) > 0) ||
                (comparator == null && (((Comparable<E>) fromElement).compareTo(toElement)) > 0)) {
            throw new IllegalArgumentException();
        }

        return subSetImpl(fromElement, fromInclusive, toElement, toInclusive);
    }

    @Override
    public AdvancedSet<E> headSet(E e, boolean b) {
        return elements.isEmpty() ? new ArraySet<>(elements, comparator) : subSetImpl(elements.getFirst(), true, e, b);
    }

    @Override
    public AdvancedSet<E> tailSet(E e, boolean b) {
        return elements.isEmpty() ? new ArraySet<>(elements, comparator) : subSetImpl(e, b, elements.getLast(), true);
    }

    @Override
    public Comparator<? super E> comparator() {
        return comparator;
    }

    @Override
    public AdvancedSet<E> subSet(E e, E e1) {
        return subSet(e, true, e1, false);
    }

    @Override
    public AdvancedSet<E> headSet(E e) {
        return headSet(e, false);
    }

    @Override
    public AdvancedSet<E> tailSet(E e) {
        return tailSet(e, true);
    }

    @Override
    public E first() {
        return elements.getFirst();
    }

    @Override
    public E last() {
        return elements.getLast();
    }

    private class ArrayMap<E, T> extends AbstractMap<E, T> {
        private final ArraySet<E> set;
        private final T value;

        private ArrayMap(ArraySet<E> set, T value) {
            this.set = set;
            this.value = value;
        }

        @Override
        public int size() {
            return set.size();
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean containsKey(Object key) {
            return set.contains((E)key);
        }

        @Override
        public boolean containsValue(Object value) {
            return !set.isEmpty() && Objects.equals(this.value, value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public T get(Object key) {
            return set.contains((E)key) ? value : null;
        }

        @Override
        public Set<E> keySet() {
            return this.set;
        }

        @Override
        public Collection<T> values() {
            return Collections.nCopies(size(), this.value);
        }

        private class MySet extends AbstractSet<Entry<E, T>> {
            @Override
            public int size() {
                return elements.size();
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean contains(Object o) {
                Entry<E, T> val = (Entry<E, T>) o;
                return elements.contains(val.getKey()) && Objects.equals(val.getValue(), value);
            }

            private class ArrayIterator implements Iterator<Entry<E, T>> {
                Iterator<E> it;

                private ArrayIterator(Iterator<E> it) {
                    this.it = it;
                }

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public Entry<E, T> next() {
                    if (!hasNext()) {
                        throw new IndexOutOfBoundsException();
                    }
                    return new SimpleImmutableEntry<>(it.next(), value);
                }
            }

            @Override
            public Iterator<Entry<E, T>> iterator() {
                return new ArrayIterator(set.iterator());
            }
        }

        @Override
        public Set<Entry<E, T>> entrySet() {
            return new MySet();
        }
    }

    @Override
    public <V> Map<E, V> asMap(V v) {
        return new ArrayMap<>(this, v);
    }
}


