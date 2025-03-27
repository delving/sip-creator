/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.grpc;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;

public class MappingServer {
    private static final Logger logger = LoggerFactory.getLogger(MappingServer.class);

    private final int port;
    private final Server server;
    private final String basePath;

    public MappingServer(int port, String basePath) {
        this.port = port;
        this.basePath = basePath;
        this.server = ServerBuilder.forPort(port)
                .addService(new MappingServiceImpl(basePath))
                .addService(ProtoReflectionService.newInstance())
                .build();
    }

    public void start() throws IOException {
        server.start();
        logger.info("Server started, listening on port " + port);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                logger.info("Shutting down gRPC server due to JVM shutdown");
                MappingServer.this.stop();
                logger.info("Server shut down");
            }
        });
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

}
