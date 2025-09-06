#!/bin/bash

javac -d . ../../java-advanced-2025/modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor/ImplerException.java
javac -d . ../../java-advanced-2025/modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor/Impler.java
javac -d . ../../java-advanced-2025/modules/info.kgeorgiy.java.advanced.implementor.tools/info/kgeorgiy/java/advanced/implementor/tools/JarImpler.java
javac -d . ../info/kgeorgiy/ja/ulin/implementor/Implementor.java

jar cfm Implementor.jar MANIFEST.MF info

rm -rf info
