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

package org.elasticsearch.common.xcontent;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.xcontent.support.DelegatingXContentParser;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

/**
 * Registry of "named" XContent parsers that can read using {@link XContentParser#namedXContent(Class, String, Object)}.
 */
public class NamedXContentRegistry {
    /**
     * Parses an object with the type {@code T} from parser.
     */
    public interface FromXContent<T, C> {
        T fromXContent(XContentParser parser, C context) throws IOException;
    }

    /**
     * Entry that will be added to the registry.
     */
    public static class Entry {
        /** The class that this entry can read. */
        public final Class<?> categoryClass;

        /** A name for the entry which is unique within the {@link #categoryClass}. */
        public final String name;

        /** A parser for which returns subclasses of {@link #categoryClass}. */
        public final FromXContent<?, ?> fromXContent;

        /** Creates a new entry which can be stored by the registry. */
        public <T> Entry(Class<T> categoryClass, String name, FromXContent<? extends T, ?> fromXContent) {
            this.categoryClass = Objects.requireNonNull(categoryClass);
            this.name = Objects.requireNonNull(name);
            this.fromXContent = Objects.requireNonNull(fromXContent);
        }
    }
    
    private final Map<Class<?>, Map<String, FromXContent<?, ?>>> registry;

    /**
     * Build the registry from a list of the entries that should be in it.
     */
    public NamedXContentRegistry(List<Entry> entries) {
        registry = unmodifiableMap(entries.stream().collect(groupingBy(e -> e.categoryClass,
                collectingAndThen(toMap(e -> e.name, e -> e.fromXContent, (name, fromXContent) -> {
                    throw new IllegalArgumentException("[" + name + "] already registered");
                }), Collections::unmodifiableMap))));
    }

    /**
     * Wrap an {@link XContentParser} in one that implements {@link XContentParser#namedXContent(Class, String, Object)} against this
     * registry.
     */
    public XContentParser wrap(XContentParser parser) {
        assert false == parser instanceof WrappedParser : "Don't rewrap parsers";
        return new WrappedParser(parser, this);
    }

    /**
     * Wrap {@code parserToWrap} with the registry from {@code wrappedParser} if {@code wrappedParser} was built with
     * {@link #wrap(XContentParser)}, throwing an exception otherwise.
     */
    public static XContentParser wrap(XContentParser wrappedParser, XContentParser parserToWrap) {
        if (false == wrappedParser instanceof WrappedParser) {
            throw new ElasticsearchException("provided wrappedParser wasn't wrapped");
        }
        return ((WrappedParser)wrappedParser).registry.wrap(parserToWrap);
    }

    /**
     * Lookup a reader, throwing an exception if the reader isn't found.
     */
    private <T, C> FromXContent<? extends T, C> getFromXContent(Class<T> categoryClass, String name, XContentLocation location) {
        Map<String, FromXContent<?, ?>> parsers = registry.get(categoryClass);
        if (parsers == null) {
            // UnsupportedOperationException because this is always a bug in Elasticsearch or a plugin
            throw new UnsupportedOperationException("Unknown NamedXContent category [" + categoryClass.getName() + "]");
        }
        @SuppressWarnings("unchecked")
        FromXContent<? extends T, C> reader = (FromXContent<? extends T, C>) parsers.get(name);
        if (reader == null) {
            // ParsingException because this is *likely* a misspelled component in a user provided query
            throw new ParsingException(location, "Unknown NamedXContent [" + categoryClass.getName() + "][" + name + "]");
        }
        return reader;
    }

    private static class WrappedParser extends DelegatingXContentParser {
        final NamedXContentRegistry registry;

        private WrappedParser(XContentParser delegate, NamedXContentRegistry registry) {
            super(delegate);
            this.registry = registry;
        }

        @Override
        public <T> T namedXContent(Class<T> categoryClass, String name, Object context) throws IOException {
            return registry.getFromXContent(categoryClass, name, getTokenLocation()).fromXContent(this, context);
        }
    }
}
