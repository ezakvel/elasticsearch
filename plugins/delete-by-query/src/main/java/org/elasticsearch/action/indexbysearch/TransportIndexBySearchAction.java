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

package org.elasticsearch.action.indexbysearch;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.TransportBulkAction;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.TransportClearScrollAction;
import org.elasticsearch.action.search.TransportSearchAction;
import org.elasticsearch.action.search.TransportSearchScrollAction;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.plugin.deletebyquery.DeleteByQueryPlugin;
import org.elasticsearch.script.CompiledScript;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.lookup.SourceLookup;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.util.HashMap;
import java.util.Map;

public class TransportIndexBySearchAction extends HandledTransportAction<IndexBySearchRequest, IndexBySearchResponse> {
    public static final ScriptContext.Plugin SCRIPT_CONTEXT = new ScriptContext.Plugin(DeleteByQueryPlugin.NAME, "index-by-search");
    private final TransportSearchAction searchAction;
    private final TransportSearchScrollAction scrollAction;
    private final TransportBulkAction bulkAction;
    private final TransportClearScrollAction clearScrollAction;
    private final ScriptService scriptService;

    @Inject
    public TransportIndexBySearchAction(Settings settings, ThreadPool threadPool, ActionFilters actionFilters,
            IndexNameExpressionResolver indexNameExpressionResolver,
            TransportSearchAction transportSearchAction,
            TransportSearchScrollAction transportSearchScrollAction, TransportBulkAction bulkAction,
            TransportClearScrollAction clearScrollAction, TransportService transportService, ScriptService scriptService) {
        super(settings, IndexBySearchAction.NAME, threadPool, transportService, actionFilters, indexNameExpressionResolver,
                IndexBySearchRequest.class);
        this.searchAction = transportSearchAction;
        this.scrollAction = transportSearchScrollAction;
        this.bulkAction = bulkAction;
        this.clearScrollAction = clearScrollAction;
        this.scriptService = scriptService;
    }

    @Override
    protected void doExecute(IndexBySearchRequest request, ActionListener<IndexBySearchResponse> listener) {
        new AsyncIndexBySearchAction(request, listener).start();
    }

    class AsyncIndexBySearchAction extends AsyncScrollAction<IndexBySearchRequest, IndexBySearchResponse> {
        public AsyncIndexBySearchAction(IndexBySearchRequest request, ActionListener<IndexBySearchResponse> listener) {
            super(logger, searchAction, scrollAction, bulkAction, clearScrollAction, request, request.search(), listener);
        }

        @Override
        protected BulkRequest buildBulk(SearchHit[] docs) {
            BulkRequest bulkRequest = new BulkRequest(mainRequest);
            ExecutableScript script = null;
            if (mainRequest.script() != null) {
                CompiledScript compiled = scriptService.compile(mainRequest.script(), SCRIPT_CONTEXT, mainRequest);
                script = scriptService.executable(compiled, mainRequest.script().getParams());
            }
            for (SearchHit doc : docs) {
                IndexRequest index = new IndexRequest(mainRequest.index(), mainRequest);

                // We want the index from the copied request, not the doc.
                index.id(doc.id());
                if (index.type() == null) {
                    /*
                     * Default to doc's type if not specified in request so its
                     * easy to do a scripted update.
                     */
                    index.type(doc.type());
                }
                // TODO routing?
                if (mainRequest.script() == null) {
                    index.source(doc.sourceRef());
                } else {
                    Tuple<XContentType, Map<String, Object>> sourceAndType = SourceLookup.sourceAsMapAndType(doc.sourceRef());
                    Map<String, Object> ctx = new HashMap<>();
                    ctx.put("_source", sourceAndType.v2());
                    ctx = runAndFetchCtx(script, ctx);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> source = (Map<String, Object>) ctx.get("_source");
                    index.source(source, sourceAndType.v1());

                    String operation = (String) ctx.get("op");
                    if (operation != null) {
                        switch(operation) {
                        //TODO Its possible to switch operations here
                        }
                    }
                }
                // TODO version?

                SearchHitField parent = doc.field("_parent");
                if (parent != null) {
                    index.parent((String) parent.value());
                }
                SearchHitField timestamp = doc.field("_timestamp");
                if (timestamp != null) {
                    // Comes back as a Long but needs to be a string
                    index.timestamp(timestamp.value().toString());
                }
                SearchHitField ttl = doc.field("_ttl");
                if (ttl != null) {
                    index.ttl((Long) ttl.value());
                }

                bulkRequest.add(index);
            }
            return bulkRequest;
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> runAndFetchCtx(ExecutableScript executable, Map<String, Object> ctx) {
            executable.setNextVar("ctx", ctx);
            executable.run();
            return (Map<String, Object>) executable.unwrap(ctx);
        }

        @Override
        protected IndexBySearchResponse buildResponse() {
            return new IndexBySearchResponse(indexed(), created());
        }
    }
}
