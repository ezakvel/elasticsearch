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
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.ParseFieldMatcherSupplier;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.xcontent.support.DelegatingXContentParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.unmodifiableMap;

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
        public final ParseField name;

        /** A parser for which returns subclasses of {@link #categoryClass}. */
        public final FromXContent<?, ?> fromXContent;

        /** Creates a new entry which can be stored by the registry. */
        public <T> Entry(Class<T> categoryClass, ParseField name, FromXContent<? extends T, ?> fromXContent) {
            this.categoryClass = Objects.requireNonNull(categoryClass);
            this.name = Objects.requireNonNull(name);
            this.fromXContent = Objects.requireNonNull(fromXContent);
        }
    }
    
    private final Map<Class<?>, Map<String, Entry>> registry;

    /**
     * Build the registry from a list of the entries that should be in it.
     */
    public NamedXContentRegistry(List<Entry> entries) {
        Map<Class<?>, Map<String, Entry>> registry = new HashMap<>();
        for (Entry entry : entries) {
            Map<String, Entry> parsers = registry.get(entry.categoryClass);
            if (parsers == null) {
                parsers = new HashMap<>();
                registry.put(entry.categoryClass, parsers);
            }
            for (String name : entry.name.getAllNamesIncludedDeprecated()) {
                Object old = parsers.putIfAbsent(name, entry);
                if (old != null) {
                    throw new IllegalArgumentException(
                            "Duplicate named XContent parsers for [" + entry.categoryClass.getName() + "][" + entry.name + "]");
                }
            }
        }
        registry.replaceAll((categoryClass, map) -> unmodifiableMap(map));
        this.registry = unmodifiableMap(registry);
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
    private <T, C> FromXContent<? extends T, C> getFromXContent(Class<T> categoryClass, String name, ParseFieldMatcher matcher,
            XContentLocation location) {
        Map<String, Entry> parsers = registry.get(categoryClass);
        if (parsers == null) {
            // UnsupportedOperationException because this is always a bug in Elasticsearch or a plugin
            throw new UnsupportedOperationException("Unknown NamedXContent category [" + categoryClass.getName() + "]");
        }
        Entry entry = parsers.get(name);
        if (entry == null || false == matcher.match(name, entry.name)) {
            // ParsingException because this is *likely* a misspelled component in a user provided query
            throw new ParsingException(location, "Unknown NamedXContent [" + categoryClass.getName() + "][" + name + "]");
        }
        @SuppressWarnings("unchecked")
        FromXContent<? extends T, C> fromXContent = (FromXContent<? extends T, C>) entry.fromXContent;
        return fromXContent;
    }

    private static class WrappedParser extends DelegatingXContentParser {
        final NamedXContentRegistry registry;

        private WrappedParser(XContentParser delegate, NamedXContentRegistry registry) {
            super(delegate);
            this.registry = registry;
        }

        @Override
        public <T, C extends ParseFieldMatcherSupplier> T namedXContent(Class<T> categoryClass, String name, C context)
                throws IOException {
            return registry.getFromXContent(categoryClass, name, context.getParseFieldMatcher(), getTokenLocation())
                    .fromXContent(this, context);
        }
    }
}
