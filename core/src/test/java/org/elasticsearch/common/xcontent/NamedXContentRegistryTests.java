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

import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.util.Arrays;

import static java.util.Collections.emptyList;
import static org.hamcrest.Matchers.containsString;

public class NamedXContentRegistryTests extends ESTestCase {
    public void testEmpty() throws IOException {
        new NamedXContentRegistry(emptyList()); // does not throw exception
    }

    public void testBasic() throws IOException {
        Object test1 = new Object();
        Object test2 = new Object();
        NamedXContentRegistry registry = new NamedXContentRegistry(Arrays.asList(
                new NamedXContentRegistry.Entry(Object.class, "test1", (p, c) -> test1),
                new NamedXContentRegistry.Entry(Object.class, "test2", (p, c) -> test2)));
        XContentParser parser = registry.wrap(JsonXContent.jsonXContent.createParser("{}"));
        assertEquals(test1, parser.namedXContent(Object.class, "test1", null));
        assertEquals(test2, parser.namedXContent(Object.class, "test2", null));
        Exception e = expectThrows(UnsupportedOperationException.class, () ->
            parser.namedXContent(NamedXContentRegistry.class, "test2", null));
        assertEquals("Unknown NamedXContent category [" + NamedXContentRegistry.class.getName() + "]", e.getMessage());
        e = expectThrows(ParsingException.class, () -> parser.namedXContent(Object.class, "dne", null));
        assertEquals("Unknown NamedXContent [java.lang.Object][dne]", e.getMessage());
    }

    public void testDuplicates() throws IOException {
        Exception e = expectThrows(IllegalArgumentException.class, () -> new NamedXContentRegistry(Arrays.asList(
                    new NamedXContentRegistry.Entry(Object.class, "test1", (p, c) -> null),
                    new NamedXContentRegistry.Entry(Object.class, "test1", (p, c) -> null))));
        assertThat(e.getMessage(), containsString("] already registered"));
    }
}
