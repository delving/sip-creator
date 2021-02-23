/*
 * Copyright 2015 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.delving.sipgrpc;

import eu.delving.groovy.*;
import eu.delving.metadata.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.w3c.dom.Node;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Server that manages startup/shutdown of a {@code Greeter} server.
 */
public class MappingRunnerServer {
    private static final Logger logger = Logger.getLogger(MappingRunnerServer.class.getName());

    private Server server;

    private void start() throws IOException {
        /* The port on which the server should run */
        int port = 50051;
        server = ServerBuilder.forPort(port)
            .addService(new GreeterImpl())
            .addService(new RunnerImpl())
            .build()
            .start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                MappingRunnerServer.this.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main launches the server from the command line.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        final MappingRunnerServer server = new MappingRunnerServer();
        server.start();
        server.blockUntilShutdown();
    }


    private class RunnerImpl extends RunnerGrpc.RunnerImplBase {

        @Override
        public void runMapping(Request req, StreamObserver<Result> responseObserver) {
            Result result = null;
            try {
                result = read(req);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (XMLStreamException e) {
                e.printStackTrace();
            } catch (MappingException e) {
                e.printStackTrace();
            }
            responseObserver.onNext(result);
            responseObserver.onCompleted();
        }

        public MetadataRecordFactory getMetadataRecordFactory(RecDefTree recDefTree) {
            Map<String, String> namespaces = new TreeMap<String, String>();
            for (RecDef.Namespace ns : recDefTree.getRecDef().namespaces) {
                namespaces.put(ns.prefix, ns.uri);
            }
            return new MetadataRecordFactory(namespaces);
        }

        public Result read(Request request) throws FileNotFoundException, UnsupportedEncodingException, XMLStreamException, MappingException {
            File recDefFname = new File("./../sip-core/src/test/resources/edm_5.2.6_record-definition.xml");
            logger.info("test run path: " + recDefFname.getAbsolutePath());
            RecDef recDef = RecDef.read(new FileInputStream(recDefFname));


            RecDefTree recDefTree = RecDefTree.create(recDef);

            File mappingFname = new File("./../sip-core/src/test/resources/mapping_edm.xml");

            RecMapping recMapping = RecMapping.read(new FileInputStream((mappingFname)), recDefTree);

            MetadataRecordFactory metadataRecordFactory = getMetadataRecordFactory(recDefTree);

            BulkMappingRunner runner = new BulkMappingRunner(recMapping, new CodeGenerator(recMapping).toRecordMappingCode());

            MetadataRecord metadataRecord = metadataRecordFactory.metadataRecordFrom(request.getRecord());

            XmlSerializer serializer = new XmlSerializer();

            Node node = runner.runMapping(metadataRecord);


            MappingResult result = new MappingResult(serializer, request.getLocalID(), node, recDefTree);

            String xml = result.toXml();

            Result resultMessage = Result.newBuilder()
                .setRecord(xml)
                .build();

            return resultMessage;

        }
    }

    private class GreeterImpl extends GreeterGrpc.GreeterImplBase {

        @Override
        public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + req.getName()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void sayHelloAgain(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello again " + req.getName()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

}
