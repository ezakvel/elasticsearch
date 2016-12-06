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

package org.elasticsearch.common.xcontent.support;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.ParseFieldMatcherSupplier;
import org.elasticsearch.common.xcontent.XContentLocation;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * {@link XContentParser} that delegates all calls to another parser. Override to implement extensions that should delegate all but a few
 * methods to some other parser.
 */
public abstract class DelegatingXContentParser implements XContentParser {
    private XContentParser delegate;

    protected DelegatingXContentParser(XContentParser delegate) {
        this.delegate = delegate;
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public XContentType contentType() {
        return delegate.contentType();
    }

    @Override
    public Token nextToken() throws IOException {
        return delegate.nextToken();
    }

    @Override
    public void skipChildren() throws IOException {
        delegate.skipChildren();
    }

    @Override
    public Token currentToken() {
        return delegate.currentToken();
    }

    @Override
    public String currentName() throws IOException {
        return delegate.currentName();
    }

    @Override
    public Map<String, Object> map() throws IOException {
        return delegate.map();
    }

    @Override
    public Map<String, Object> mapOrdered() throws IOException {
        return delegate.mapOrdered();
    }

    @Override
    public Map<String, String> mapStrings() throws IOException {
        return delegate.mapStrings();
    }

    @Override
    public Map<String, String> mapStringsOrdered() throws IOException {
        return delegate.mapStringsOrdered();
    }

    @Override
    public List<Object> list() throws IOException {
        return delegate.list();
    }

    @Override
    public List<Object> listOrderedMap() throws IOException {
        return delegate.listOrderedMap();
    }

    @Override
    public String text() throws IOException {
        return delegate.text();
    }

    @Override
    public String textOrNull() throws IOException {
        return delegate.textOrNull();
    }

    @Override
    public BytesRef utf8BytesOrNull() throws IOException {
        return delegate.utf8BytesOrNull();
    }

    @Override
    public BytesRef utf8Bytes() throws IOException {
        return delegate.utf8Bytes();
    }

    @Override
    public Object objectText() throws IOException {
        return delegate.objectText();
    }

    @Override
    public Object objectBytes() throws IOException {
        return delegate.objectBytes();
    }

    @Override
    public boolean hasTextCharacters() {
        return delegate.hasTextCharacters();
    }

    @Override
    public char[] textCharacters() throws IOException {
        return delegate.textCharacters();
    }

    @Override
    public int textLength() throws IOException {
        return delegate.textLength();
    }

    @Override
    public int textOffset() throws IOException {
        return delegate.textOffset();
    }

    @Override
    public Number numberValue() throws IOException {
        return delegate.numberValue();
    }

    @Override
    public NumberType numberType() throws IOException {
        return delegate.numberType();
    }

    @Override
    public short shortValue(boolean coerce) throws IOException {
        return delegate.shortValue(coerce);
    }

    @Override
    public int intValue(boolean coerce) throws IOException {
        return delegate.intValue(coerce);
    }

    @Override
    public long longValue(boolean coerce) throws IOException {
        return delegate.longValue(coerce);
    }

    @Override
    public float floatValue(boolean coerce) throws IOException {
        return delegate.floatValue(coerce);
    }

    @Override
    public double doubleValue(boolean coerce) throws IOException {
        return delegate.doubleValue(coerce);
    }

    @Override
    public short shortValue() throws IOException {
        return delegate.shortValue();
    }

    @Override
    public int intValue() throws IOException {
        return delegate.intValue();
    }

    @Override
    public long longValue() throws IOException {
        return delegate.longValue();
    }

    @Override
    public float floatValue() throws IOException {
        return delegate.floatValue();
    }

    @Override
    public double doubleValue() throws IOException {
        return delegate.doubleValue();
    }

    @Override
    public boolean isBooleanValue() throws IOException {
        return delegate.isBooleanValue();
    }

    @Override
    public boolean booleanValue() throws IOException {
        return delegate.booleanValue();
    }

    @Override
    public byte[] binaryValue() throws IOException {
        return delegate.binaryValue();
    }

    @Override
    public XContentLocation getTokenLocation() {
        return delegate.getTokenLocation();
    }

    @Override
    public <T, C extends ParseFieldMatcherSupplier> T namedObject(Class<T> type, String name, C context) throws IOException {
        return delegate.namedObject(type, name, context);
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }
}
