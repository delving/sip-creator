package eu.delving.sip.webservice;

import com.google.gson.Gson;
import eu.delving.sip.MappingCLI;
import eu.delving.sip.files.HomeDirectory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MappingServiceHandler extends HttpServlet {

    private final Gson gson = new Gson();
    private final static Path tmpDir = getTmpDir();

    private static Path getTmpDir() {
        Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
        if (!Files.exists(tmpDir) || !Files.isDirectory(tmpDir)) {
            throw new IllegalStateException("Tmp dir is not an existing directory: " + tmpDir);
        }
        return tmpDir;
    }

    protected void doPost(
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException {

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

        SIPRequest sipRequest = gson.fromJson(request.getReader(), SIPRequest.class);
        System.out.println(sipRequest);
        byte[] input = sipRequest.input.getBytes(StandardCharsets.UTF_8);
        Path mappingFile = fetchFile(sipRequest.mappingURL);
        Path recordDefinitionFile = fetchFile(sipRequest.recordDefinitionURL);

        MappingCLI mappingCLI = new MappingCLI();
        try {
            mappingCLI.startMapping(input, mappingFile, recordDefinitionFile, null, tmpDir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        response.getWriter().println("{ \"status\": \"ok\"}");
    }

    private Path fetchFile(String url) throws IOException {
        String[] parts = url.split("/");
        String identifier = parts[parts.length - 1];

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] readBuffer = new byte[4048];
        try (InputStream in = new URL(url).openStream()) {
            int bytesRead;
            while ((bytesRead = in.read(readBuffer)) != -1)
                buffer.write(readBuffer, 0, bytesRead);
        }

        Path file = HomeDirectory.WORK_DIR.toPath().resolve(identifier);
        Files.write(file, buffer.toByteArray());
        return file;
    }
}
