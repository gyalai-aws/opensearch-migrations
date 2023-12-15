package org.opensearch.migrations.replay.tracing;

import lombok.NonNull;
import org.opensearch.migrations.tracing.IScopedInstrumentationAttributes;
import org.opensearch.migrations.tracing.IWithTypedEnclosingScope;

public class DirectNestedSpanContext<T extends IScopedInstrumentationAttributes>
        extends AbstractNestedSpanContext<T>
        implements IWithTypedEnclosingScope<T> {
    public DirectNestedSpanContext(@NonNull T enclosingScope) {
        super(enclosingScope);
    }

    @Override
    public T getLogicalEnclosingScope() {
        return (T) getEnclosingScope();
    }
}
