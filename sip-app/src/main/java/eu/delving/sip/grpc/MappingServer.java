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

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 50051; // Default gRPC port
        String basePath = "";
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
            basePath = args[1];
        }

        final MappingServer server = new MappingServer(port, basePath);
        server.start();
        server.blockUntilShutdown();
    }
}
