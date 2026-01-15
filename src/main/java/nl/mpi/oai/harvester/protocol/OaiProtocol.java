package nl.mpi.oai.harvester.protocol;

import nl.mpi.oai.harvester.Provider;
import nl.mpi.oai.harvester.StaticProvider;
import nl.mpi.oai.harvester.action.ActionSequence;
import nl.mpi.oai.harvester.control.Configuration;
import nl.mpi.oai.harvester.control.FileSynchronization;
import nl.mpi.oai.harvester.control.Main;
import nl.mpi.oai.harvester.control.Util;
import nl.mpi.oai.harvester.cycle.Cycle;
import nl.mpi.oai.harvester.cycle.Endpoint;
import nl.mpi.oai.harvester.harvesting.*;
import nl.mpi.oai.harvester.metadata.MetadataFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

/**
 * This class represents a single processing thread in the harvesting actions
 * workflow. In practice one worker takes care of one provider. The worker
 * applies a scenario for harvesting: first get record identifiers, after that
 * get the records individually. Alternatively, in a second scenario, it gets
 * multiple records per OAI request directly.
 *
 * @author Lari Lampen (MPI-PL), extensions by Kees Jan van de Looij (MPI-PL)
 * extensions by Vic Ding (HUC/DI KNAW)
 */
public class OaiProtocol extends Protocol {
    private final Logger logger = LogManager.getLogger(this.getClass());

    /**
     * The configuration
     */
    private final Configuration config;

    /**
     * The provider this worker deals with.
     */
    private final Provider provider;

    /**
     * List of actionSequences to be applied to the harvested metadata.
     */
    private final List<ActionSequence> actionSequences;

    /* Harvesting scenario to be applied. ListIdentifiers: first, based on
       endpoint data and prefix, get a list of identifiers, and after that
       retrieve each record in the list individually. ListRecords: skip the
       list, retrieve multiple records per request.
     */
    private final String scenarioName;

    // kj: annotate
    Endpoint endpoint;

    /**
     * Associate a provider and action actionSequences with a scenario
     *
     * @param provider OAI-PMH provider that this thread will harvest
     * @param cycle    the harvesting cycle
     */
    public OaiProtocol(Provider provider, Configuration config, Cycle cycle) {
        super(provider, config, cycle);

        this.config = config;

        this.provider = provider;

        this.actionSequences = config.getActionSequences();

        // register the endpoint with the cycle, kj: get the group
        endpoint = cycle.next(provider.getOaiUrl(), "group");

        // get the name of the scenario the worker needs to apply
        this.scenarioName = provider.getScenario();
    }

    @Override
    public void run() {
        Throwable t = null;
        try {
            logger.info("Welcome to OAI Harvest Manager worker!");
            provider.init();

            Thread.currentThread().setName(provider.getName().replaceAll("[^a-zA-Z0-9\\-\\(\\)]", " "));

            // setting specific log filename
            ThreadContext.put("logFileName", Util.toFileFormat(provider.getName()).replaceAll("/", ""));

            String map = config.getMapFile();
            String workDir = config.getWorkingDirectory();
            map = workDir + "/" + map;
            synchronized (map) {
                try (PrintWriter m = new PrintWriter(new FileWriter(map, true))) {
                    if (config.hasRegistryReader()) {
                        m.println(config.getRegistryReader().endpointMapping(provider.getOaiUrl(), provider.getName()));
                    } else {
                        m.printf("%s,%s,%s,", provider.getOaiUrl(), Util.toFileFormat(provider.getName()).replaceAll("/", ""), provider.getName());
                        m.println();
                    }
                } catch (IOException e) {
                    logger.error("failed to write to the map file!", e);
                }
            }

            boolean done = false;

            // factory for metadata records
            MetadataFactory metadataFactory = new MetadataFactory();

            // factory for OAI verbs
            OAIFactory oaiFactory = new OAIFactory();

            logger.info("Processing provider[" + provider + "] using scenario[" + scenarioName + "], incremental[" + provider.getIncremental() + "], timeout[" + provider.getTimeout() + "] and retry[count=" + provider.getMaxRetryCount() + ",delays=" + Arrays.toString(provider.getRetryDelays()) + "]");

            FileSynchronization.addProviderStatistic(provider);

            for (final ActionSequence actionSequence : actionSequences) {

                if (config.isDryRun()) {
                    logger.info("Dry run mode. Skipping action sequence: {{}}", actionSequence.toString());
                } else {
                    // list of prefixes provided by the endpoint
                    List<String> prefixes;

                    // kj: annotate
                    OAIScenario scenario = new OAIScenario(provider, actionSequence);

                    if (provider instanceof StaticProvider) {
                        logger.debug("static harvest[" + provider + "]");

                        // set type of format harvesting to apply
                        AbstractHarvesting harvesting = new StaticPrefixHarvesting(
                                oaiFactory,
                                (StaticProvider) provider,
                                actionSequence);
                        logger.debug("harvesting[" + harvesting + "]");

                        // get the prefixes
                        prefixes = scenario.getPrefixes(harvesting);
                        logger.debug("prefixes[" + prefixes + "]");

                        if (prefixes.isEmpty()) {
                            logger.debug("no prefixes[" + prefixes + "] -> done");
                            done = false;
                        } else {
                            // set type of record harvesting to apply
                            harvesting = new StaticRecordListHarvesting(oaiFactory,
                                    (StaticProvider) provider, prefixes, metadataFactory);

                            // get the records
                            if (scenarioName.equals("ListIdentifiers")) {
                                done = scenario.listIdentifiers(harvesting);
                                logger.debug("list identifiers -> done[" + done + "]");
                            } else {
                                done = scenario.listRecords(harvesting);
                                logger.debug("list records -> done[" + done + "]");
                            }
                        }
                    } else {
                        logger.debug("dynamic harvest[" + provider + "]");

                        // set type of format harvesting to apply
                        AbstractHarvesting harvesting = new FormatHarvesting(oaiFactory,
                                provider, actionSequence);

                        // get the prefixes
                        prefixes = scenario.getPrefixes(harvesting);
                        logger.debug("prefixes[" + prefixes + "]");

                        if (prefixes.isEmpty()) {
                            // no match
                            logger.debug("no prefixes[" + prefixes + "] -> done");
                            done = false;
                        } else {
                            // determine the type of record harvesting to apply
                            if (scenarioName.equals("ListIdentifiers")) {
                                // kj: annotate, connect verb to scenario
                                harvesting = new IdentifierListHarvesting(oaiFactory,
                                        provider, prefixes, metadataFactory, endpoint);

                                // get the records
                                done = scenario.listIdentifiers(harvesting);
                                logger.debug("list identifiers -> done[" + done + "]");
                            } else {
                                harvesting = new RecordListHarvesting(oaiFactory,
                                        provider, prefixes, metadataFactory, endpoint);

                                // get the records
                                done = scenario.listRecords(harvesting);
                                logger.debug("list records -> done[" + done + "]");
                            }
                            if (Main.config.isIncremental()) {
                                FileSynchronization.execute(provider);
                            }
                        }
                    }
                }
                // break after an action sequence has completed successfully
                if (done) break;
            }

            // report back success or failure to the cycle
            endpoint.doneHarvesting(done);
            if (config.isIncremental()) {
                FileSynchronization.saveStatistics(provider);
                endpoint.setIncrement(FileSynchronization.getProviderStatistic(provider).getHarvestedRecords());
            }
            logger.info("Processing finished for " + provider);
        } catch (Throwable e) {
            logger.error("Processing failed for " + provider + ": " + e.getMessage(), e);
            t = e;
            throw e;
        } finally {
            provider.close();

            ThreadContext.clearAll();

            // tell the main log how it went
            if (t != null)
                logger.error("Processing failed for " + provider + ": " + t.getMessage(), t);
            else
                logger.info("Processing finished for " + provider);

            logger.debug("Goodbye from OAI Harvest Manager worker!");
        }
    }
}
