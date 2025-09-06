package info.kgeorgiy.ja.ulin.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.tools.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;

import static java.nio.file.Files.newBufferedWriter;

/**
 * Class, that can generate Implementation of given class
 */
public class Implementor implements JarImpler {
    /**
     * Default tabs
     */
    private static final String TABS = "    ";

    /**
     * Default constructor, that creates a Implementor
     */
    public Implementor() {

    }

    /**
     * Create a <var>.jar</var> file.
     * <p>
     * Generate <var>.jar</var> file from 1 argument <var>.java</var> file, and write it to 2 argument path.
     * </p>
     *
     * @param args argument for write <var>.jar</var> file.
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Expected 2 argument, get " + args.length);
            return;
        }

        if (!Objects.equals(args[0], "-jar")) {
            System.err.println("Expected run with -jar key");
        }

        try {
            Class<?> aClass = Class.forName(args[0]);
            Path outputFile = Path.of(args[1]);

            new Implementor().implementJar(aClass, outputFile);
        } catch (ClassNotFoundException e) {
            System.err.println("Cannot find given class: " + e.getMessage());
        } catch (ImplerException e) {
            System.err.println("Cannot implement for given class: " + e.getMessage());
        }
    }

    /**
     * Writes a given string to a file using Unicode escape sequences.
     * <p>
     * Each character in the input string is converted to its Unicode escape sequence
     * before being written to the file.
     * </p>
     *
     * @param writer the writer used to write to the file.
     * @param write the string to be written.
     * @throws IOException if an I/O error occurs while writing to the file.
     */
    private static void writeToFile(BufferedWriter writer, String write) throws IOException {
        char[] charset = write.toCharArray();

        for (final char c : charset) {
            writer.write(String.format("\\u%04x", (int) c));
        }
    }

    /**
     * Return the default value for a given type.
     * <p>
     * The default value is determined as follows:
     * <ul>
     *      <li>{@code void} return empty string {@code ""}</li>
     *      <li>{@code boolean} return {@code "false"}</li>
     *      <li>Other primitive types return {@code "0"}</li>
     *      <li>Reference types return {@code "null"}</li>
     * </ul>
     *
     * @param rType the returned type, fow that need get a default value.
     * @return a string representation of the default value.
     */
    private static String getDefaultValue(Class<?> rType) {
        if (rType == void.class) return "";
        if (rType == boolean.class) return "false";
        if (rType.isPrimitive()) return "0";
        return "null";
    }

    /**
     * Generate the string for method arguments.
     * <p>
     * If no one arguments, then return an empty string.
     * </p>
     *
     * @param params array of arguments types.
     * @param isVararg {@code trye} id last argument is vararg, {@code false} otherwise.
     * @return a formated separated string, that contains the all arguments. Arguments will separated {@code ", "}
     *         and {@code i-th} argument will have name {@code "arg" + i}.
     */
    private static String getArgs(Class<?>[] params, boolean isVararg) {
        return IntStream.range(0, params.length)
                .mapToObj(i -> {
                    String name = params[i].getCanonicalName();
                    if (i + 1 == params.length && isVararg) {
                        name = name.replace("[]", "...");
                    }
                    return name + " arg" + i;
                })
                .collect(Collectors.joining(", "));
    }

    /**
     * Generate the string for throws errors.
     * <p>
     * If the {@param errors} is empty, then return empty string.
     * </p>
     *
     * @param errors array of exception types.
     * @return a formated strings start with {@code " throws "} followed by a {@code ", "} separated list
     *         of exceptions or an empty string, if {@code errors} is empty.
     */
    private static String getErrors(Class<?>[] errors) {
        if (errors.length == 0) {
            return "";
        }

        return " throws " + Arrays.stream(errors)
                .map(Class::getCanonicalName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Generate the header for method or constructor.
     * <p>
     * The generated header include the method or constructor name, parameter list, and declared exceptions.
     * If any parameter has a private type, an exception is thrown.
     * </p>
     *
     * @param name name of the header.
     * @param args arguments of the header.
     * @param isVarargs {@code true} if last argument is varargs, {@code false} otherwise.
     * @param errors errors, that can throw the header
     * @return value is a string, that contains a header
     * @throws ImplerException if provided method cannot be generated.
     */
    private static String getHeader(String name, Class<?>[] args, boolean isVarargs, Class<?>[] errors) throws ImplerException {
        StringBuilder ans = new StringBuilder();

        ans.append(name);
        ans.append("(");

        boolean hasPrivateArgs = Arrays.stream(args).anyMatch(a -> Modifier.isPrivate(a.getModifiers()));
        if (hasPrivateArgs) {
            throw new ImplerException("Cannot implement, class has private arguments in methods");
        }

        ans.append(getArgs(args, isVarargs));

        ans.append(")");
        ans.append(getErrors(errors));
        ans.append(" {");

        return ans.toString();
    }

    /**
     * Generate the given method.
     * <p>
     * Method returned the default value of the returned type. If method is private,
     * it is skipped. If method with same signature exists in {@code set}, it will skip.
     * </p>
     *
     * @param method method of superclass to be implemented.
     * @param set set of methods signatures.
     * @param writer writer to output generated code.
     * @throws ImplerException if provided method cannot be generated.
     * @throws IOException when an I/O errors occurs.
     */
    private static void writeMethod(Method method, Set<String> set, BufferedWriter writer) throws ImplerException, IOException {
        if (Modifier.isPrivate(method.getModifiers())) {
            return;
        }

        if (Modifier.isPrivate(method.getReturnType().getModifiers())) {
            throw new ImplerException("Cannot implement, class has private returned type");
        }

        StringBuilder methodSignature = new StringBuilder();

        methodSignature.append(" ").append(method.getReturnType().getCanonicalName()).append(" ");
        methodSignature.append(getHeader(method.getName(), method.getParameterTypes(), method.isVarArgs(), method.getExceptionTypes()));

        if (set.contains(methodSignature.toString())) {
            return;
        }

        set.add(methodSignature.toString());

        if (!Modifier.isAbstract(method.getModifiers()) || Modifier.isFinal(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
            return;
        }

        writeToFile(writer, TABS);
        writeToFile(writer, Modifier.toString(method.getModifiers() & ~Modifier.TRANSIENT & ~Modifier.ABSTRACT));

        writeToFile(writer, methodSignature.toString());

        writer.newLine();
        writeToFile(writer, TABS + TABS);
        writeToFile(writer, "return " + getDefaultValue(method.getReturnType()) + ";");
        writer.newLine();

        writeToFile(writer, TABS + "}");
        writer.newLine();
    }

    /**
     * Generate the method for the given class.
     * <p>
     * If some constructors exist in set, write nothing. If some methods have private arguments or returned type,
     * throw an exception.
     * </p>
     *
     * @param aClass token those methods should be implemented.
     * @param writer writer to output generated code.
     * @param set set with method headers.
     * @param getMethods function, what from class return a methods.
     * @throws ImplerException when implementation is not possible.
     * @throws IOException when an I/O errors occurs.
     */
    private static void writeMethods(Class<?> aClass, BufferedWriter writer, Set<String> set, Function<Class<?>, Method[]> getMethods) throws IOException, ImplerException {
        Method[] methods = getMethods.apply(aClass);

        for (Method method : methods) {
            writeMethod(method, set, writer);
        }
    }

    /**
     * Generate the method for the given class ant his superclasses.
     * <p>
     * If some methods have private arguments or returned type, throw an exception.
     * </p>
     *
     * @param aClass token those methods should be implemented.
     * @param writer writer to output generated code.
     * @throws ImplerException when implementation is not possible.
     * @throws IOException when an I/O errors occurs.
     */
    private static void methodHandler(Class<?> aClass, BufferedWriter writer) throws IOException, ImplerException {
        Set<String> set = new HashSet<>();

        writeMethods(aClass, writer, set, Class::getMethods);

        while (Objects.nonNull(aClass)) {
            writeMethods(aClass, writer, set, Class::getDeclaredMethods);
            aClass = aClass.getSuperclass();
        }
    }

    /**
     * Generate the given constructor.
     * <p>
     * Constructor calls the superclass constructor with the same parameters. If constructor is private,
     * it is skipped.
     * </p>
     *
     * @param className name the generated implementation class.
     * @param constructor constructor of the superclass to be implemented.
     * @param writer writer to output generated code.
     * @return {@code true} if the constructor write successfully,  {@code false} if it was skipped.
     * @throws IOException when an I/O errors occurs.
     */
    private static boolean writeConstructor(String className, Constructor<?> constructor, BufferedWriter writer) throws IOException {
        if (Modifier.isPrivate(constructor.getModifiers())) {
            return false;
        }

        StringBuilder signature = new StringBuilder();

        try {
            signature.append("public ").append(getHeader(className, constructor.getParameterTypes(), constructor.isVarArgs(), constructor.getExceptionTypes()));
        } catch (ImplerException e) {
            return false;
        }

        writeToFile(writer, TABS + signature.toString());

        writer.newLine();
        writeToFile(writer, TABS + TABS + "super(");
        for (int i = 0; i < constructor.getParameterCount(); ++i) {
            writeToFile(writer, "arg" + Integer.toString(i));
            if (i + 1 != constructor.getParameterCount()) {
                writeToFile(writer, ", ");
            }
        }
        writeToFile(writer, ");");
        writer.newLine();

        writeToFile(writer, TABS + "}");
        writer.newLine();

        return true;
    }

    /**
     * Generate the constructors for the given class, or nothing, if interface.
     * <p>
     * If not private constructor not exists, throws an exception.
     * </p>
     *
     * @param aClass token those constructors should be implemented.
     * @param writer writer to output generated code.
     * @throws ImplerException when implementation is not possible.
     * @throws IOException when an I/O errors occurs.
     */
    private static void writeConstructors(Class<?> aClass, BufferedWriter writer) throws IOException, ImplerException {
        Constructor<?>[] constructors = aClass.getDeclaredConstructors();

        boolean ok = false;

        for (Constructor<?> constructor : constructors) {
            ok |= writeConstructor(aClass.getSimpleName() + "Impl", constructor, writer);
        }

        if (!ok && !aClass.isInterface()) {
            throw new ImplerException("Cannot implement, constructor not found");
        }
    }

    /**
     * Check what the given class or interface can be implemented.
     * <p>
     * Throws an exception if the class is final, sealed, private, enum, record, or lacks accessible constructors.
     * </p>
     *
     * @param aClass token class to check.
     * @throws ImplerException when implementation is not possible.
     */
    private static void tryImplement(Class<?> aClass) throws ImplerException {
        boolean hasAccessibleConstructor = Arrays.stream(aClass.getDeclaredConstructors()).anyMatch(c -> !Modifier.isPrivate(c.getModifiers()));

        if (!aClass.isInterface() && !hasAccessibleConstructor || aClass.isPrimitive() || Modifier.isFinal(aClass.getModifiers()) || aClass.isSealed() ||
                Modifier.isPrivate(aClass.getModifiers()) || aClass.isAssignableFrom(Enum.class) || aClass.isAssignableFrom(Record.class)) {
            throw new ImplerException("Cannot generate class");
        }
    }

    /**
     * Return a string, that contains a package name.
     *
     * @param aClass token, which package need to get.
     * @return a formated string, start with {@code "package "}, that contains a full code for package.
     * @throws ImplerException if package start with {@code java}.
     */
    private static String getPackageName(Class<?> aClass) throws ImplerException {
        if (aClass.getPackageName().startsWith("java")) {
            throw new ImplerException("Cannot implement from java package");
        }

        return "package " + aClass.getPackageName() + ";";
    }

    /**
     * Return a string, that contains a class signature.
     *
     * @param aClass token, which signature need to get.
     * @return a formated string, start with {@code "public class "}, that contains a full code for header.
     */
    private static String getClassSignature(Class<?> aClass) {
        return "public class " + aClass.getSimpleName() + "Impl " + (aClass.isInterface() ? "implements" : "extends") + " " + aClass.getCanonicalName();
    }

    /**
     * Produces <var>.java</var> file implementing class or interface specified by provided <var>token</var>.
     * <p>
     * Generated class' name will have the same as the class name of the type token with <var>Impl</var> suffix
     * added.
     * </p>
     *
     * @param aClass type token to create implementation for.
     * @param path target <var>.java</var> file.
     * @throws ImplerException when implementation cannot be generated.
     */
    @Override
    public void implement(Class<?> aClass, Path path) throws ImplerException {
        tryImplement(aClass);

        String className = aClass.getSimpleName();

        path = path.resolve(aClass.getPackageName().replace('.', '/')).resolve(className + "Impl.java");

        try {
            Files.createDirectories(path.getParent());
        } catch (IOException ignore) {
        }


        try (BufferedWriter outputFile = newBufferedWriter(path.toAbsolutePath(), StandardCharsets.UTF_8)) {
            writeToFile(outputFile, getPackageName(aClass));

            outputFile.newLine();
            outputFile.newLine();

            writeToFile(outputFile, getClassSignature(aClass) + " {");
            outputFile.newLine();

            writeConstructors(aClass, outputFile);
            methodHandler(aClass, outputFile);

            writeToFile(outputFile, "}");
            outputFile.newLine();
        } catch (IOException e) {
            System.err.println("Error while writing in file: " + e.getMessage());
        }
    }

    /**
     * Produces <var>.jar</var> file implementing class or interface specified by provided <var>token</var>.
     * <p>
     * Generated class' name will have the same as the class name of the type token with <var>Impl</var> suffix
     * added.
     * </p>
     *
     * @param aClass type token to create implementation for.
     * @param path target <var>.jar</var> file.
     * @throws ImplerException when implementation cannot be generated.
     */
    @Override
    public void implementJar(Class<?> aClass, Path path) throws ImplerException {
        Path buildDir;

        try {
            buildDir = Files.createTempDirectory(path.getParent(), "__Build__Jar__");
        } catch (IOException e) {
            throw new ImplerException("Cannot create build directory");
        }

        buildDir.toFile().deleteOnExit();

        implement(aClass, buildDir);

        compile(aClass, buildDir);
        createJar(buildDir, getFullPath(aClass, "class"), path);
    }

    /**
     * Compile the previously generated implementation of the given class or interface.
     * <p>
     * Used the system Java compiler to generate <var>.class</var> file from the <var>.java</var> file.
     * </p>
     *
     * @param aClass type token whose implementation should be compiled.
     * @param root root directory where the generated <var>.java</var> file is located.
     * @throws ImplerException when <var>.class</var> file cannot be generated.
     */
    private static void compile(Class<?> aClass, Path root) throws ImplerException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (!Objects.nonNull(compiler)) {
            throw new ImplerException("Could not find java compiler, include tools.jar to classpath");
        }

        Path classpath;
        try {
            classpath = Path.of(aClass.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new ImplerException(e.getMessage());
        }

        String[] args = {root.resolve(getFullPath(aClass, "java").toString()).toString(), "-cp", classpath.toString()};

        final int exitCode = compiler.run(null, null, null, args);
        if (exitCode != 0) {
            throw new ImplerException("Compiler exit code " + exitCode);
        }
    }

    /**
     * Return a {@code Path} to token.
     * <p>
     * Return the {@code Path} to given token with {@code "Impl." + extension} suffix.
     * </p>
     *
     * @param aClass token, which path need to get.
     * @param extension extension of file.
     * @return a {@code Path} to given token.
     */
    private static Path getFullPath(Class<?> aClass, String extension) {
        return Path.of(aClass.getPackageName().replace('.', File.separatorChar))
                .resolve(aClass.getSimpleName() + "Impl." + extension);
    }

    /**
     * Creates a <var>.jar</var> file contained the compiled implementation of the given class or interface
     * <p>
     * The generated <var>JAR</var> includes the compiled <var>Impl</var> class and a manifest.
     * </p>
     *
     * @param root root directory where <var>.class</var> file is located.
     * @param classFile path to compiled <var>.class</var> file.
     * @param jarFile root directory where <var>.jar</var> file will be located.
     * @throws ImplerException when <var>.jar</var> cannot be generated.
     */
    private static void createJar(Path root, Path classFile, Path jarFile) throws ImplerException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        try (var jarStream = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            jarStream.putNextEntry(new ZipEntry(classFile.toString().replace(File.separatorChar, '/')));
            Files.copy(root.resolve(classFile), jarStream);
        } catch (IOException e) {
            throw new ImplerException("Error while creating Jar: " + e.getMessage());
        }
    }
}
