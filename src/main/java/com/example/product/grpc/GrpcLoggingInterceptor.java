package com.example.product.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

/**
 * Logs every unary RPC method name. Useful for local debugging without a full tracing stack.
 */
@Component
@GlobalServerInterceptor
public class GrpcLoggingInterceptor implements ServerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(GrpcLoggingInterceptor.class);

	@Override
	public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
			ServerCall<ReqT, RespT> call,
			Metadata headers,
			ServerCallHandler<ReqT, RespT> next) {
		log.info("gRPC {}", call.getMethodDescriptor().getFullMethodName());
		return next.startCall(call, headers);
	}
}
