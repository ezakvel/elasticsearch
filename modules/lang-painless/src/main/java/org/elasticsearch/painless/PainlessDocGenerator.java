/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.painless;

import org.apache.lucene.util.IOUtils;
import org.elasticsearch.common.io.PathUtils;
import org.elasticsearch.painless.Definition.Method;
import org.elasticsearch.painless.Definition.Struct;
import org.elasticsearch.painless.Definition.Type;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

/**
 * Generates an API reference from the method and type whitelists in {@link Definition}.
 */
public class PainlessDocGenerator {
    private static final Comparator<Method> METHOD_NAME = comparing(m -> m.name);
    private static final Comparator<Method> NUMBER_OF_ARGS = comparing(m -> m.arguments.size());

    public static void main(String[] args) throws IOException {
        Path apiRootPath = PathUtils.get(args[0]);

        // Blow away the last execution and recreate it from scratch
        IOUtils.rm(apiRootPath);
        Files.createDirectories(apiRootPath);

        Path indexPath = apiRootPath.resolve("index.asciidoc");
        System.out.println("Starting to write " + indexPath);
        try (PrintStream indexStream = new PrintStream(indexPath.toFile(), StandardCharsets.UTF_8.name())) {
            List<Type> types = Definition.allSimpleTypes().stream().sorted(comparing(t -> t.name)).collect(toList());
            for (Type type : types) {
                if (type.sort.primitive) {
                    // Primitives don't have methods to reference
                    continue;
                }
                if ("def".equals(type.name)) {
                    // def is special but doesn't have any methods all of its own.
                    continue;
                }
                indexStream.print("include::");
                indexStream.print(type.struct.name);
                indexStream.println(".asciidoc[]");

                Path typePath = apiRootPath.resolve(type.struct.name + ".asciidoc");
                System.out.println("Writing " + type.name);
                try (PrintStream typeStream = new PrintStream(typePath.toFile(), StandardCharsets.UTF_8.name())) {
                    typeStream.print("* [[");
                    emitAnchor(typeStream, type.struct);
                    typeStream.print("]]");
                    typeStream.println(type.name);
                    Consumer<Method> documentMethod = method -> PainlessDocGenerator.documentMethod(typeStream, method);
                    type.struct.staticMethods.values().stream().sorted(METHOD_NAME.thenComparing(NUMBER_OF_ARGS)).forEach(documentMethod);
                    type.struct.constructors.values().stream().sorted(NUMBER_OF_ARGS).forEach(documentMethod);
                    Map<String, Struct> inherited = new TreeMap<>();
                    type.struct.methods.values().stream().sorted(METHOD_NAME.thenComparing(NUMBER_OF_ARGS)).forEach(method -> {
                        if (method.owner == type.struct) {
                            documentMethod(typeStream, method);
                        } else {
                            inherited.put(method.owner.name, method.owner);
                        }
                    });

                    if (false == inherited.isEmpty()) {
                        typeStream.print("** Inherits methods from ");
                        boolean first = true;
                        for (Struct inheritsFrom : inherited.values()) {
                            if (first) {
                                first = false;
                            } else {
                                typeStream.print(", ");
                            }
                            emitStruct(typeStream, inheritsFrom);
                        }
                        typeStream.println();
                    }
                    // NOCOMMIT fields and static fields
                }
            }
        }
        System.out.println("Done writing " + indexPath);
    }

    /**
     * Document a method.
     */
    private static void documentMethod(PrintStream stream, Method method) {
        // NOCOMMIT little chain icon for linking
        stream.print("** [[");
        emitAnchor(stream, method);
        stream.print("]]");

        if (false == method.augmentation && Modifier.isStatic(method.modifiers)) {
            stream.print("static ");
        }

        if (false == method.name.equals("<init>")) {
            emitType(stream, method.rtn);
            stream.print(' ');
        }

        String javadocRoot = javadocRoot(method);
        emitJavadocLink(stream, javadocRoot, method);
        stream.print('[');

        stream.print(methodName(method));

        stream.print("](");
        boolean first = true;
        for (Type arg : method.arguments) {
            if (first) {
                first = false;
            } else {
                stream.print(", ");
            }
            emitType(stream, arg);
        }
        stream.print(")");

        if (javadocRoot.equals("java8")) {
            stream.print(" (");
            emitJavadocLink(stream, "java9", method);
            stream.print("[java 9])");
        }

        stream.println();
    }

    /**
     * Anchor text for a type.
     */
    private static void emitAnchor(PrintStream stream, Struct struct) {
        stream.print("painless-api-");
        // Use the struct's name because it doesn't include any array markers ([]) which we never want to include in these anchors
        stream.print(struct.name.replace('.', '-'));
    }

    /**
     * Anchor text for a method.
     */
    private static void emitAnchor(PrintStream stream, Method method) {
        emitAnchor(stream, method.owner);
        stream.print('-');
        stream.print(methodName(method));
        stream.print('-');
        stream.print(method.arguments.size());
    }

    private static String methodName(Method method) {
        return method.name.equals("<init>") ? method.owner.name : method.name;
    }

    /**
     * Emit a {@link Type}. If the type is primitive or an array of primitives this just emits the name of the type. Otherwise this emits an
     * internal link with the text.
     */
    private static void emitType(PrintStream stream, Type type) {
        emitStruct(stream, type.struct);
        for (int i = 0; i < type.dimensions; i++) {
            stream.print("[]");
        }
    }

    /**
     * Emit a {@link Struct}. If the {@linkplain Struct} is primitive or def this just emits the name of the struct. Otherwise this emits an
     * internal link with the name.
     */
    private static void emitStruct(PrintStream stream, Struct struct) {
        if (false == struct.clazz.isPrimitive() && false == struct.name.equals("def")) {
            stream.print("<<");
            emitAnchor(stream, struct);
            stream.print(',');
            stream.print(struct.name);
            stream.print(">>");
        } else {
            stream.print(struct.name);
        }
    }

    /**
     * Emit an external link to some javadoc.
     *
     * @param root name of the root uri variable
     * @param method the method to link to
     */
    private static void emitJavadocLink(PrintStream stream, String root, Method method) {
        stream.print("link:{");
        stream.print(root);
        stream.print("-javadoc}/");
        stream.print((method.augmentation ? Augmentation.class : method.owner.clazz).getName().replace('.', '/'));
        stream.print(".html#");
        stream.print(methodName(method));
        stream.print("%2D");
        boolean first = true;
        if (method.augmentation) {
            first = false;
            stream.print(method.owner.clazz.getName());
            stream.print("%2D");
        }
        for (Type arg: method.arguments) {
            if (first) {
                first = false;
            } else {
                stream.print("%2D");
            }
            stream.print(arg.struct.clazz.getName());
            if (arg.dimensions > 0) {
                stream.print(":A");
            }
        }
        stream.print("%2D");
    }

    /**
     * Pick the javadoc root for some type.
     */
    private static String javadocRoot(Method method) {
        if (method.augmentation) {
            return "painless";
        }
        String classPackage = method.owner.clazz.getPackage().getName();
        if (classPackage.startsWith("java")) {
            return "java8";
        }
        if (classPackage.startsWith("org.elasticsearch.painless")) {
            return "painless";
        }
        if (classPackage.startsWith("org.elasticsearch")) {
            return "elasticsearch";
        }
        if (classPackage.startsWith("org.joda.time")) {
            return "joda-time";
        }
        throw new IllegalArgumentException("Unrecognized packge: " + classPackage);
    }
}
