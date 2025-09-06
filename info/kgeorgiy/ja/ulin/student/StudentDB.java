package info.kgeorgiy.ja.ulin.student;

import info.kgeorgiy.java.advanced.student.*;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class StudentDB implements AdvancedQuery {
    private static <T> Stream<T> getInfo(final Collection<Student> collection, final Function<Student, T> func) {
        return collection.stream().map(func);
    }

    private static <T> List<T> getInfoList(final Collection<Student> collection, final Function<Student, T> func) {
        return getInfo(collection, func).toList();
    }

    private static Stream<Student> sortStream(final Stream<Student> streamStudents, final Comparator<Student> comparator) {
        return streamStudents.sorted(comparator);
    }

    private static Stream<Student> sortStreamStudentsByName(final Stream<Student> streamStudents) {
        return sortStream(streamStudents,
                // :NOTE: const
                Comparator.comparing(Student::firstName).thenComparing(Student::lastName).thenComparing(Student::id));
    }

    private static Stream<Student> sortStreamStudentsById(final Stream<Student> streamStudents) {
        return sortStream(streamStudents,
                Comparator.comparingInt(Student::id));
    }

    private static Stream<Student> sortStudentsByNameStream(final Collection<Student> students) {
        return sortStreamStudentsByName(students.stream());
    }

    private static Stream<Student> sortStudentsByIdStream(final Collection<Student> students) {
        return sortStreamStudentsById(students.stream());
    }

    private static List<Student> sortStudentsByNameList(final Collection<Student> students) {
        return sortStreamStudentsByName(students.stream()).toList();
    }

    private static List<Student> sortStudentsByIdList(final Collection<Student> students) {
        return sortStreamStudentsById(students.stream()).toList();
    }

    private static List<Group> getSortedGroups(final Stream<Student> streamStudents) {
        return streamStudents
                .collect(Collectors.groupingBy(Student::groupName))
                // :NOTE: interim map
                .entrySet()
                .stream()
                .map(group -> new Group(group.getKey(), group.getValue()))
                .sorted(Comparator.comparing(Group::name))
                .toList();
    }

    private static <T> List<T> getInfoList(final Collection<Group> groups, final int[] indexes, final Function<Student, T> func) {
        return groups.stream()
                .flatMap(group -> IntStream.of(indexes)
                        // :NOTE: filter + map
                        .filter(i -> 0 <= i && i < group.students().size())
                        .mapToObj(group.students()::get)
                        .map(func)
                )
                .toList();
    }

    private static <T, U extends Collection<?>> GroupName getGroup(
            final Collection<Student> students,
            final Comparator<GroupName> comparator,
            final Function<Student, T> func,
            final Collector<T, ?, U> collection
    ) {
        return students.stream()
                .collect(Collectors.groupingBy(Student::groupName, Collectors.mapping(func, collection)))
                .entrySet()
                // :NOTE: map
                .stream()
                // :NOTE: Map.Entry.comparingByKey()
                .max(Comparator.comparingInt((Map.Entry<GroupName, ? extends Collection<?>> e) -> e.getValue().size())
                        .thenComparing(Map.Entry::getKey, comparator)
                )
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static <T> Stream<Student> findStudentsStream(final Collection<Student> students, final T find, final Function<Student, T> func) {
        return sortStreamStudentsByName(
                students.stream()
                        .filter(student -> Objects.equals(func.apply(student), find))
        );
    }

    private static <T> List<Student> findStudents(final Collection<Student> students, final T find, final Function<Student, T> func) {
        return findStudentsStream(students, find, func).toList();
    }

    private static String getPopularName(
            final Collection<Group> students,
            final Comparator<? super Map.Entry<String, Long>> comparator
    ) {
        return students.stream()
                .flatMap(group -> group.students().stream().map(Student::firstName).distinct())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                // :NOTE: map
                .entrySet().stream()
                .max(comparator)
                .map(Map.Entry::getKey)
                .orElse("");
    }

    @Override
    public List<Group> getGroupsByName(final Collection<Student> collection) {
        return getSortedGroups(sortStudentsByNameStream(collection));
    }

    @Override
    public List<Group> getGroupsById(final Collection<Student> collection) {
        return getSortedGroups(sortStudentsByIdStream(collection));
    }

    @Override
    public GroupName getLargestGroup(final Collection<Student> students) {
        return getGroup(students, Comparator.naturalOrder(), Student::id, Collectors.toList());
    }

    @Override
    public GroupName getLargestGroupFirstName(final Collection<Student> students) {
        return getGroup(students, Comparator.reverseOrder(), Student::firstName, Collectors.toSet());
    }

    @Override
    public List<String> getFirstNames(final List<Student> students) {
        return getInfoList(students, Student::firstName);
    }

    @Override
    public List<String> getLastNames(final List<Student> students) {
        // :NOTE: ??
        return getInfo(students, Student::lastName).toList();
    }

    @Override
    public List<GroupName> getGroupNames(final List<Student> students) {
        return getInfoList(students, Student::groupName);
    }

    @Override
    public List<String> getFullNames(final List<Student> students) {
        return getInfoList(students, student -> student.firstName() + " " + student.lastName());
    }

    @Override
    public Set<String> getDistinctFirstNames(final List<Student> students) {
        return getInfo(students, Student::firstName).collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public String getMaxStudentFirstName(final List<Student> students) {
        return students.stream()
                // :NOTE: comparingInt
                .max(Comparator.comparing(Student::id))
                .map(Student::firstName)
                .orElse("");
    }

    @Override
    public List<Student> sortStudentsById(final Collection<Student> students) {
        return sortStudentsByIdList(students);
    }

    @Override
    public List<Student> sortStudentsByName(final Collection<Student> students) {
        return sortStudentsByNameList(students);
    }

    @Override
    public List<Student> findStudentsByFirstName(final Collection<Student> students, final String firstName) {
        return findStudents(students, firstName, Student::firstName);
    }

    @Override
    public List<Student> findStudentsByLastName(final Collection<Student> students, final String lastName) {
        return findStudents(students, lastName, Student::lastName);
    }

    @Override
    public List<Student> findStudentsByGroup(final Collection<Student> students, final GroupName groupName) {
        return findStudents(students, groupName, Student::groupName);
    }

    @Override
    public Map<String, String> findStudentNamesByGroup(final Collection<Student> collection, final GroupName groupName) {
        return findStudentsStream(collection, groupName, Student::groupName)
                .collect(Collectors.toMap(
                        Student::lastName,
                        Student::firstName,
                        BinaryOperator.minBy(String::compareTo)
                ));
    }

    @Override
    public String getMostPopularName(final Collection<Group> students) {
        return getPopularName(students, Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()));
    }

    @Override
    public String getLeastPopularName(final Collection<Group> students) {
        // :NOTE: simplify
        return getPopularName(students, Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()));
    }

    @Override
    public List<String> getFirstNames(final Collection<Group> groups, final int[] indexes) {
        return getInfoList(groups, indexes, Student::firstName);
    }

    @Override
    public List<String> getLastNames(final Collection<Group> groups, final int[] indexes) {
        // :NOTE: getLastNames
        return getInfoList(groups, indexes, Student::lastName);
    }

    @Override
    public List<GroupName> getGroupNames(final Collection<Group> groups, final int[] indexes) {
        return getInfoList(groups, indexes, Student::groupName);
    }

    @Override
    public List<String> getFullNames(final Collection<Group> groups, final int[] indexes) {
        return getInfoList(groups, indexes, student -> student.firstName() + " " + student.lastName());
    }
}
