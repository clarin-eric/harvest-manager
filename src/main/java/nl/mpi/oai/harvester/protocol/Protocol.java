package nl.mpi.oai.harvester.protocol;

import nl.mpi.oai.harvester.Provider;
import nl.mpi.oai.harvester.action.ActionSequence;
import nl.mpi.oai.harvester.control.*;
import nl.mpi.oai.harvester.cycle.Cycle;
import nl.mpi.oai.harvester.cycle.Endpoint;
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
public abstract class Protocol implements Runnable {

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
    public Protocol(Provider provider, Configuration config,
                  Cycle cycle) {

        this.config = config;

        this.provider = provider;

        this.actionSequences = config.getActionSequences();

        // register the endpoint with the cycle, kj: get the group
        this.endpoint = cycle.next(provider.getOaiUrl(), "group");

        // get the name of the scenario the worker needs to apply
        this.scenarioName = provider.getScenario();
    }

    public abstract void run();
}
