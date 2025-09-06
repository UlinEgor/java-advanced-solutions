module info.kgeorgiy.ja.ulin {
    requires info.kgeorgiy.java.advanced.arrayset;
    requires info.kgeorgiy.java.advanced.implementor;
    requires info.kgeorgiy.java.advanced.implementor.tools;
    requires info.kgeorgiy.java.advanced.lambda;
    requires info.kgeorgiy.java.advanced.student;
    requires java.compiler;
    requires info.kgeorgiy.java.advanced.mapper;
    requires info.kgeorgiy.java.advanced.crawler;
    requires java.desktop;
    requires info.kgeorgiy.java.advanced.hello;
    requires java.rmi;
    requires jdk.httpserver;

    exports info.kgeorgiy.ja.ulin.arrayset;
    exports info.kgeorgiy.ja.ulin.implementor;
    exports info.kgeorgiy.ja.ulin.iterative;
    exports info.kgeorgiy.ja.ulin.lambda;
    exports info.kgeorgiy.ja.ulin.student;
    exports info.kgeorgiy.ja.ulin.walk;
    exports info.kgeorgiy.ja.ulin.hello;
    exports info.kgeorgiy.ja.ulin.crawler;
}