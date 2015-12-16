package org.elasticsearch.plugin.indexbysearch;

import static java.lang.Math.min;
import static org.elasticsearch.action.ValidateActions.addValidationError;
import static org.elasticsearch.search.sort.SortBuilders.fieldSort;

import java.io.IOException;
import java.util.Arrays;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.builder.SearchSourceBuilder;

public abstract class AbstractBulkByScrollRequest<Self extends AbstractBulkByScrollRequest<Self>>
        extends ActionRequest<Self> {
    private static final TimeValue DEFAULT_SCROLL_TIMEOUT = TimeValue.timeValueMinutes(5);
    private static final int DEFAULT_SIZE = 100;

    /**
     * The search to be executed.
     */
    private SearchRequest source;

    /**
     * Maximum number of processed documents. Defaults to -1 meaning process all
     * documents.
     */
    private int size = -1;

    /**
     * Should version conflicts cause aborts? Defaults to false.
     */
    private boolean abortOnVersionConflict = false;

    public AbstractBulkByScrollRequest() {
    }

    public AbstractBulkByScrollRequest(SearchRequest source) {
        this.source = source;

        // Set the defaults which differ from SearchRequest's defaults.
        source.scroll(DEFAULT_SCROLL_TIMEOUT);
        source.source(new SearchSourceBuilder());
        source.source().version(true);
        source.source().sort(fieldSort("_doc"));
        source.source().size(DEFAULT_SIZE);
    }

    /**
     * `this` cast to Self. Used for building fluent methods without cast
     * warnings.
     */
    protected abstract Self self();

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException e = source.validate();
        if (source.source().from() != -1) {
            e = addValidationError("from is not supported in this context", e);
        }
        return e;
    }

    /**
     * Maximum number of processed documents. Defaults to -1 meaning process all
     * documents.
     */
    public int size() {
        return size;
    }

    /**
     * Maximum number of processed documents. Defaults to -1 meaning process all
     * documents.
     */
    public Self size(int size) {
        this.size = size;
        return self();
    }

    /**
     * Should version conflicts cause aborts? Defaults to false.
     */
    public boolean abortOnVersionConflict() {
        return abortOnVersionConflict;
    }

    /**
     * Should version conflicts cause aborts? Defaults to false.
     */
    public Self abortOnVersionConflict(boolean abortOnVersionConflict) {
        this.abortOnVersionConflict = abortOnVersionConflict;
        return self();
    }

    /**
     * Sets abortOnVersionConflict based on REST-friendly names.
     */
    public void conflicts(String conflicts) {
        switch (conflicts) {
        case "proceed":
            abortOnVersionConflict(false);
            return;
        case "abort":
            abortOnVersionConflict(true);
            return;
        default:
            throw new IllegalArgumentException("conflicts may only be \"proceed\" or \"abort\" but was [" + conflicts + "]");
        }
    }

    /**
     * The search request that matches the documents to process.
     */
    public SearchRequest source() {
        return source;
    }

    public void fillInConditionalDefaults() {
        // NOCOMMIT move this to implementations
        if (size() != -1) {
            /*
             * Don't use larger batches than the maximum request size because
             * that'd be silly.
             */
            source().source().size(min(size(), source().source().size()));
        }
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        source.readFrom(in);
        size = in.readVInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        source.writeTo(out);
        out.writeVInt(size);
    }

    /**
     * Append a short description of the search request to a StringBuilder. Used
     * to make toString.
     */
    protected void searchToString(StringBuilder b) {
        if (source.indices() != null && source.indices().length != 0) {
            b.append(Arrays.toString(source.indices()));
        } else {
            b.append("[all indices]");
        }
        if (source.types() != null && source.types().length != 0) {
            b.append(source.types());
        }
    }

}
