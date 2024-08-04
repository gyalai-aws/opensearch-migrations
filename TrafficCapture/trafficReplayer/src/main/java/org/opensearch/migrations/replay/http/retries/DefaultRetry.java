package org.opensearch.migrations.replay.http.retries;

import io.netty.buffer.ByteBuf;
import org.opensearch.migrations.replay.AggregatedRawResponse;
import org.opensearch.migrations.replay.IRequestResponsePacketPair;
import org.opensearch.migrations.replay.RequestResponsePacketPair;
import org.opensearch.migrations.replay.RequestSenderOrchestrator;
import org.opensearch.migrations.replay.util.TextTrackedFuture;
import org.opensearch.migrations.replay.util.TrackedFuture;

import java.util.List;
import java.util.Optional;

public class DefaultRetry implements RequestRetryEvaluator {
    static final int MAX_RETRIES = 4;

    public static boolean doNotRetryRequestFromResultAlone(int statusCode) {
        if (statusCode >= 300 && statusCode < 400) {
            return true;
        }
        switch (statusCode) {
            case 200:
            case 201:
            case 401:
            case 403:
                return true;
            default:
                return false;
        }
    }

    public TextTrackedFuture<RequestSenderOrchestrator.RetryDirective>
    getRetryDirectiveUnlessExceededMaxRetries(List<AggregatedRawResponse> previousResponses) {
        var d = previousResponses.size() > MAX_RETRIES ?
            RequestSenderOrchestrator.RetryDirective.DONE :
            RequestSenderOrchestrator.RetryDirective.RETRY;
        return TextTrackedFuture.completedFuture(d, () -> "determined if we should retry or return the response now");
    }

    TrackedFuture<String, RequestSenderOrchestrator.RetryDirective>
    makeDeterminationFuture(RequestSenderOrchestrator.RetryDirective d, String msg) {
        return TextTrackedFuture.completedFuture(d, () -> msg);
    }

    @Override
    public TrackedFuture<String, RequestSenderOrchestrator.RetryDirective>
    apply(ByteBuf targetRequestBytes,
          List<AggregatedRawResponse> previousResponses,
          AggregatedRawResponse currentResponse,
          TrackedFuture<String, ? extends IRequestResponsePacketPair> reconstructedSourceTransactionFuture) {
        if (doNotRetryRequestFromResultAlone(currentResponse.getRawResponse().status().code())) {
            return makeDeterminationFuture(RequestSenderOrchestrator.RetryDirective.DONE,
                "returning DONE because response code was terminal");
        }
        return getRetryDirectiveUnlessExceededMaxRetries(previousResponses);
    }
}
