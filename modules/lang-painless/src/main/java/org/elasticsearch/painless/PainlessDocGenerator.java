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
import java.util.function.Consumer;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

/**
 * Generates an API reference from the method and type whitelists in {@link Definition}.
 */
public class PainlessDocGenerator {
    private static final Comparator<Type> TYPE_SORT = comparing((Type t) -> t.name.equals("def")).reversed() 
            .thenComparing(comparing(t -> t.name));
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
            List<Type> types = Definition.allSimpleTypes().stream().sorted(TYPE_SORT).collect(toList());
            for (Type type : types) {
                if (type.sort.primitive) {
                    continue;
                }
                indexStream.print("include::");
                indexStream.print(type.struct.name);
                indexStream.println(".asciidoc[]");

                Path structPath = apiRootPath.resolve(type.struct.name + ".asciidoc");
                System.out.println("Writing " + type.name);
                try (PrintStream structStream = new PrintStream(structPath.toFile(), StandardCharsets.UTF_8.name())) {
                    structStream.print("* [[");
                    emitAnchor(structStream, type);
                    structStream.print("]]");
                    structStream.println(type.name);
                    Consumer<Method> documentMethod = method -> PainlessDocGenerator.documentMethod(structStream, type, method);
                    type.struct.staticMethods.values().stream().sorted(METHOD_NAME.thenComparing(NUMBER_OF_ARGS)).forEach(documentMethod);
                    type.struct.constructors.values().stream().sorted(NUMBER_OF_ARGS).forEach(documentMethod);
                    type.struct.methods.values().stream().sorted(METHOD_NAME.thenComparing(NUMBER_OF_ARGS)).forEach(documentMethod);
                    // NOCOMMIT methods inherited
                    // NOCOMMIT fields and static fields
                }
            }
        }
        System.out.println("Done writing " + indexPath);
    }

    /**
     * Document a method.
     *
     * @param receiver the type for which we are documenting the method. This is only the same as {@code method}'s {@link Method#owner} if
     * the {@code receiver} declared the method.
     * @param method method we are documenting
     */
    private static void documentMethod(PrintStream structStream, Type receiver, Method method) {
        // NOCOMMIT little chain icon for linking
        // NOCOMMIT augments
        structStream.print("** [[");
        emitAnchor(structStream, receiver, method);
        structStream.print("]]");

        if (Modifier.isStatic(method.modifiers)) {
            structStream.print("static ");
        }

        if (false == method.name.equals("<init>")) {
            emitType(structStream, method.rtn);
            structStream.print(' ');
        }

        String javadocRoot = javadocRoot(method.owner);
        emitJavadocLink(structStream, javadocRoot, method);
        structStream.print('[');

        structStream.print(methodName(method));

        structStream.print("](");
        boolean first = true;
        for (Type arg : method.arguments) {
            if (first) {
                first = false;
            } else {
                structStream.print(", ");
            }
            emitType(structStream, arg);
        }
        structStream.print(")");

        if (javadocRoot.equals("java8")) {
            structStream.print(" (");
            emitJavadocLink(structStream, "java9", method);
            structStream.print("[java 9])");
        }

        structStream.println();
    }

    /**
     * Anchor text for a type.
     */
    private static void emitAnchor(PrintStream stream, Type type) {
        stream.print("painless-api-");
        // Use the struct's name because it doesn't include any array markers ([]) which we never want to include in these anchors
        stream.print(type.struct.name.replace('.', '-'));
    }

    /**
     * Anchor text for a method.
     *
     * @param receiver the type for which we are anchoring. This is only the same as {@code method}'s {@link Method#owner} if the
     *        {@code receiver} declared the method.
     * @param method method anchor to emit
     */
    private static void emitAnchor(PrintStream stream, Type receiver, Method method) {
        emitAnchor(stream, receiver);
        stream.print('-');
        stream.print(methodName(method));
        stream.print('-');
        stream.print(method.arguments.size());
    }

    private static String methodName(Method method) {
        return method.name.equals("<init>") ? method.owner.name : method.name;
    }

    /**
     * Emit a type. If the type is primitive or an array of primitives this just emits the text of the type. Otherwise this emits an
     * internal link with the text.
     */
    private static void emitType(PrintStream structStream, Type type) {
        // Use type.struct.clazz.isPrimitive() instead of type.sort.primitive because the former doesn't link to primitive arrays
        if (false == type.struct.clazz.isPrimitive()) {
            structStream.print("<<");
            emitAnchor(structStream, type);
            structStream.print(',');
        }
        // Use the struct name instead of the type name because the type name includes array markers ([]) that we don't want
        structStream.print(type.struct.name);
        if (false == type.struct.clazz.isPrimitive()) {
            structStream.print(">>");
        }
        for (int i = 0; i < type.dimensions; i++) {
            structStream.print("[]");
        }
    }

    /**
     * Emit an external link to some javadoc.
     *
     * @param root name of the root uri variable
     * @param method the method to link to
     */
    private static void emitJavadocLink(PrintStream structStream, String root, Method method) {
        structStream.print("link:{");
        structStream.print(root);
        structStream.print("-javadoc}/");
        structStream.print(method.owner.clazz.getName().replace('.', '/'));
        structStream.print(".html#");
        structStream.print(methodName(method));
        structStream.print("%2D");
        for (Type arg: method.arguments) {
            structStream.print(arg.struct.clazz.getName());
            if (arg.dimensions > 0) {
                structStream.print(":A");
            }
            structStream.print("%2D");
        }
        if (method.arguments.isEmpty()) {
            structStream.print("%2D");
        }
    }

    /**
     * Pick the javadoc root for some type.
     */
    private static String javadocRoot(Struct struct) {
        String classPackage = struct.clazz.getPackage().getName();
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
