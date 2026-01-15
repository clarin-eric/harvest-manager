module oai.harvest.manager {
    exports nl.mpi.oai.harvester.action;
    exports nl.mpi.oai.harvester.metadata;
    exports nl.mpi.oai.harvester.protocol;
    exports nl.mpi.oai.harvester.utils;
    requires Saxon.HE;
    requires SaxonUtils;
    requires org.apache.logging.log4j;
    requires stax2.api;
    requires java.xml;
    requires java.xml.bind;
    requires java.management;
    requires org.joda.time;
    requires org.apache.commons.io;
    // requires xalan;
    requires json.path;
    requires com.google.common;
    requires java.logging;
    requires woodstox.core.asl;
    requires json.smart;
    requires java.activation;
   //requires unirest.java;

    opens nl.mpi.oai.harvester.generated to java.xml.bind;
}