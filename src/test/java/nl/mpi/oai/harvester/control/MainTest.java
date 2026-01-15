/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.mpi.oai.harvester.control;

import com.google.common.base.Charsets;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Function;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Vic Ding <qiqing.ding@di.huc.knaw.nl>
 */
public class MainTest {

    private final static Logger logger = LoggerFactory.getLogger(MainTest.class);

    private static final String BASIC_CONFIG_WITH_NDE_PROTOCOL = "/config/test-config-nde-protocol.xml";

    @Rule
    public TemporaryFolder workdir = new TemporaryFolder();

    private final Function<String, String> testConfigFilter
            = line -> line.replaceAll("\\{\\{workdir\\}\\}", workdir.getRoot().getAbsolutePath());

//    @Test
//    public void testRunHarvestingDefaultProtocol() throws Exception {
//        // TODO: assert something
//        final Configuration config = readConfig(BASIC_CONFIG_WITH_DEFAULT_PROTOCOL);
//        Main.runHarvesting(config);
//    }

    @Test
    public void testRunHarvestingNdeProtocol() throws Exception {
        // TODO: assert something
        final Configuration config = readConfig(BASIC_CONFIG_WITH_NDE_PROTOCOL);
        Main.runHarvesting(config);
        // TODO: let the test wait for the threads instead of waiting for 5"
        Thread.sleep(5000);
    }

    private Configuration readConfig(String name) throws Exception {
        final String filename = pathForResource(name);
        final Configuration config = new Configuration();
        config.readConfig(filename);
        return config;
    }

    private String pathForResource(String resource) {
        return fileForResource(resource, testConfigFilter).getAbsolutePath();
    }

    private File fileForResource(String resource, Function<String, String> filter) {
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Resource not found");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charsets.UTF_8.name()))) {
                final File tempFile = File.createTempFile(getClass().getName(), ".xml");
                tempFile.deleteOnExit();

                logger.debug("Resource {} -> file on disk {}", resource, tempFile.getAbsoluteFile());

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        writer.write(filter.apply(line));
                        writer.newLine();
                    }
                    return tempFile;
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("Error while reading resource for test: " + resource, ex);
        }
    }
}
