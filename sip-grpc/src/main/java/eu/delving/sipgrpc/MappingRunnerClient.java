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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple client that requests a greeting from the {@link HelloWorldServer}.
 */
public class MappingRunnerClient {
    private static final Logger logger = Logger.getLogger(MappingRunnerClient.class.getName());

    private final ManagedChannel channel;
    private final RunnerGrpc.RunnerBlockingStub blockingStub;

    /**
     * Construct client connecting to HelloWorld server at {@code host:port}.
     */
    public MappingRunnerClient(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port)
            // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
            // needing certificates.
            .usePlaintext()
            .build());
    }

    /**
     * Construct client for accessing HelloWorld server using the existing channel.
     */
    MappingRunnerClient(ManagedChannel channel) {
        this.channel = channel;
        blockingStub = RunnerGrpc.newBlockingStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Say hello to server.
     */
   /* public void greet(String name) {
        logger.info("Will try to greet " + name + " ...");
        HelloRequest request = HelloRequest.newBuilder().setName(name).build();
        HelloReply response;
        try {
            response = blockingStub.sayHello(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Greeting: " + response.getMessage());
        try {
            response = blockingStub.sayHelloAgain(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Greeting: " + response.getMessage());
    }*/

    /**
     * mapping runner
     */
    public void runMapping() {
        // logger.info("will try to run mapping");
        Request request = Request.newBuilder()
            .setOrgID("demo")
            .setDatasetID(("spec"))
            .setLocalID("123")
            .setRecord(testRecord)
            .build();

        Result result;
        result = blockingStub.runMapping(request);
        // logger.info("mapping result: " + result.getRecord());
    }

    /**
     * Greet server. If provided, the first element of {@code args} is the name to use in the
     * greeting.
     */
    public static void main(String[] args) throws Exception {
        // Access a service running on the local machine on port 50051
        MappingRunnerClient client = new MappingRunnerClient("localhost", 50051);
        try {
            for(int i=1; i<500; i++){
                System.out.println("Count is: " + i);
                client.runMapping();
            }
        } finally {
            client.shutdown();
        }
    }

    String testRecord = " <input id=\"enb-112.beeldmateriaal-957193e3-f3a9-6e4d-97ff-9f7390c41e86-005ff52b-4c5e-9a79-5ee9-50bcc65c8a77\"> <record xmlns:oai_enb=\"http://www.openarchives.org/OAI/2.0/oai_enb/\" xmlns:dc=\"http://dublincore.org/documents/dcmi-namespace/\" xmlns:mmm=\"http://api.memorix-maior.nl/REST/3.0/\" xmlns:enb_dc=\"http://purl.org/enb_dc/elements/1.1/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
        "    <header>\n" +
        "      <identifier>enb_112.beeldmateriaal:957193e3-f3a9-6e4d-97ff-9f7390c41e86:005ff52b-4c5e-9a79-5ee9-50bcc65c8a77</identifier>\n" +
        "      <datestamp>2019-05-29T23:38:02Z</datestamp>\n" +
        "    </header>\n" +
        "    <metadata>\n" +
        "      <oai_enb:dc xsi:schemaLocation=\"http://www.openarchives.org/OAI/2.0/oai_dc/          http://www.openarchives.org/OAI/2.0/oai_dc.xsd\">\n" +
        "        <enb_dc:set_title>Beeldmateriaal</enb_dc:set_title>\n" +
        "        <enb_dc:tenant>enb_112</enb_dc:tenant>\n" +
        "        <enb_dc:tenantname>Stichting Heemkundekring 'Willem van Strijen'</enb_dc:tenantname>\n" +
        "        <enb_dc:col_entiteit>957193e3-f3a9-6e4d-97ff-9f7390c41e86</enb_dc:col_entiteit>\n" +
        "        <enb_dc:entity_uuid>005ff52b-4c5e-9a79-5ee9-50bcc65c8a77</enb_dc:entity_uuid>\n" +
        "        <enb_dc:dcterms_identifier>BM300081</enb_dc:dcterms_identifier>\n" +
        "        <enb_dc:spectrum_collection_name></enb_dc:spectrum_collection_name>\n" +
        "        <enb_dc:dcterms_type>Foto</enb_dc:dcterms_type>\n" +
        "        <enb_dc:dcterms_title>Johan en Diny Goverde</enb_dc:dcterms_title>\n" +
        "        <enb_dc:dcterms_description>&lt;p&gt;Johan en Diny Goverde in de kapsalon van hun vader/moeder Driek Goverde en Cornelia Jacobs. Johan heeft de kapsalon in 1938 overgenonem van zijn vader. Diny beheerde de toiletartikelen en parfumeriëen in de winkel naast de kapsalon.&lt;/p&gt;</enb_dc:dcterms_description>\n" +
        "        <enb_dc:dcterms_subject></enb_dc:dcterms_subject>\n" +
        "        <enb_dc:dcterms_medium></enb_dc:dcterms_medium>\n" +
        "        <enb_dc:internal_notes1>A1.003.Gov.007</enb_dc:internal_notes1>\n" +
        "        <enb_dc:subcollectienaam></enb_dc:subcollectienaam>\n" +
        "        <enb_dc:geos>\n" +
        "          <enb_dc:geo>\n" +
        "            <enb_dc:straatnaam>Langenoordstraat</enb_dc:straatnaam>\n" +
        "            <enb_dc:nummer>66</enb_dc:nummer>\n" +
        "            <enb_dc:geonameid>2743947</enb_dc:geonameid>\n" +
        "            <enb_dc:name>Zevenbergen</enb_dc:name>\n" +
        "            <enb_dc:country_name>Nederland</enb_dc:country_name>\n" +
        "            <enb_dc:country_code>NL</enb_dc:country_code>\n" +
        "            <enb_dc:admin1_name>Noord-Brabant</enb_dc:admin1_name>\n" +
        "            <enb_dc:admin2_name>Gemeente Moerdijk</enb_dc:admin2_name>\n" +
        "          </enb_dc:geo>\n" +
        "        </enb_dc:geos>\n" +
        "      </oai_enb:dc>\n" +
        "    </metadata>\n" +
        "    <about>\n" +
        "      <mmm:memorix xsi:schemaLocation=\"http://api.memorix-maior.nl/REST/3.0/ http://api.memorix-maior.nl/REST/3.0/MRX-API-ANY.xsd\">\n" +
        "        <image>\n" +
        "          <thumbnail_small>http://images.memorix.nl/enb_112/thumb/220x220/d582440d-731a-5578-0517-e1a70faa88c1.jpg</thumbnail_small>\n" +
        "          <thumbnail_large>http://images.memorix.nl/enb_112/thumb/500x500/d582440d-731a-5578-0517-e1a70faa88c1.jpg</thumbnail_large>\n" +
        "          <mimetype>image/jpeg</mimetype>\n" +
        "          <filename>BM300081.jpg</filename>\n" +
        "          <dzi>http://images.memorix.nl/enb_112/deepzoom/d582440d-731a-5578-0517-e1a70faa88c1.dzi</dzi>\n" +
        "        </image>\n" +
        "        <url>http://api.memorix-maior.nl/collectiebeheer/oai-pmh?verb=GetRecord&amp;metadataPrefix=oai_enb&amp;tenant=enb&amp;key=30afd54c-8a05-11e5-a67e-00155d012a81&amp;identifier=enb_112.beeldmateriaal:957193e3-f3a9-6e4d-97ff-9f7390c41e86:005ff52b-4c5e-9a79-5ee9-50bcc65c8a77</url>\n" +
        "      </mmm:memorix>\n" +
        "    </about>\n" +
        "  </record>\n</input>";
}
