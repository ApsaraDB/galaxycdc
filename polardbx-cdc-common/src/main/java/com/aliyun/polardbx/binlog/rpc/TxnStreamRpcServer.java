/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.rpc;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.protocol.DumpRequest;
import com.aliyun.polardbx.binlog.protocol.TxnServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static io.grpc.internal.GrpcUtil.getThreadFactory;

/**
 * Created by ziyang.lb
 **/
public class TxnStreamRpcServer {

    private static final Logger logger = LoggerFactory.getLogger(TxnStreamRpcServer.class);
    private static final int MAX_INBOUND_MESSAGE_SIZE = 1024 * 1024 * 10;                                 // 10M

    private final int port;
    private final Server server;

    public TxnStreamRpcServer(int port, TxnMessageProvider provider) {
        this((NettyServerBuilder) ServerBuilder.forPort(port), port, provider);
    }

    /**
     * Create a TxnStream server using serverBuilder as a base and features as data.
     */
    public TxnStreamRpcServer(NettyServerBuilder serverBuilder, int port, TxnMessageProvider provider) {
        this.port = port;
        this.server = serverBuilder.maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
            .flowControlWindow(1048576 * 100)
            .addService(new TxnStreamRpcServer.TxnStreamingService(provider))
            .build();
    }

    /**
     * Start serving requests.
     */
    public void start() throws IOException {
        server.start();
        logger.info("Rpc Server started, listening on " + port);
    }

    /**
     * Stop serving requests and shutdown resources.
     */
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon
     * threads.
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    private static class TxnStreamingService extends TxnServiceGrpc.TxnServiceImplBase {

        private final TxnMessageProvider provider;
        private final ReentrantLock lock;
        private final ExecutorService executor;

        TxnStreamingService(TxnMessageProvider provider) {
            this.provider = provider;
            this.lock = new ReentrantLock();
            this.executor = Executors.newCachedThreadPool(getThreadFactory("txn-stream-processor" + "-%d", true));
        }

        // ???????????????????????????????????????????????????????????????????????????????????????????????????
        @Override
        public void dump(DumpRequest request, StreamObserver<DumpReply> responseObserver) {
            logger.info("Accepted a request from client side.");
            ServerCallStreamObserver<DumpReply> observer = (ServerCallStreamObserver<DumpReply>) responseObserver;
            TxnOutputStream<DumpReply> txnOutputStream = new TxnOutputStream<>(observer);
            txnOutputStream.init();

            // ??????????????????Grpc????????????dump???????????????????????????????????????????????????dump?????????????????????????????????
            // https://github.com/grpc/grpc-java/issues/7839
            // https://github.com/grpc/grpc-java/issues/7361
            executor.submit(() -> {
                try {
                    if (!lock.tryLock(10, TimeUnit.SECONDS)) {
                        String message = "try acquire lock failed, because other client is consuming.";
                        logger.warn(message);
                        responseObserver.onError(new PolardbxException(message));
                        return;
                    }

                    txnOutputStream.setExecutingThead(Thread.currentThread());
                    logger.info("The client successfully acquired lock.");
                    logger.info("request tso is : [" + request.getTso() + "]");
                    if (StringUtils.isNotBlank(request.getTso())) {
                        if (!provider.checkTSO(request.getTso(), txnOutputStream, false)) {
                            provider.restart(request.getTso());
                        }
                        // ???????????????????????????????????????????????????????????????
                        if (!provider.checkTSO(request.getTso(), txnOutputStream, true)) {
                            throw new PolardbxException("can' find binlog for tso " + request.getTso());
                        }
                    } else {
                        //??????tso?????????????????????????????????????????????????????????????????????????????????
                        provider.restart(request.getTso());
                    }

                    provider.dump(request.getTso(), txnOutputStream);

                    // ??????????????????????????????dump?????????????????????????????????????????????Provider?????????stop???????????????????????????????????????????????????
                    responseObserver.onError(new PolardbxException("server is shutdown."));
                } catch (Throwable t) {
                    logger.error("dump error!!", t);
                    responseObserver.onError(t);
                } finally {
                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            });
        }
    }
}
