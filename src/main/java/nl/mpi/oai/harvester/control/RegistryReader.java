/*
 * Copyright (C) 2015, The Max Planck Institute for
 * Psycholinguistics.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * A copy of the GNU General Public License is included in the file
 * LICENSE-gpl-3.0.txt. If that file is missing, see
 * <http://www.gnu.org/licenses/>.
 */
package nl.mpi.oai.harvester.control;

import static com.jayway.jsonpath.Criteria.where;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.Filter;
import static com.jayway.jsonpath.Filter.filter;
import com.jayway.jsonpath.JsonPath;
import static com.jayway.jsonpath.JsonPath.parse;
import com.jayway.jsonpath.Option;
import nl.mpi.oai.harvester.Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class reads information from the REST service of the CLARIN Centre
 * Registry (see http://www.clarin.eu/content/centres for more information).
 *
 * @author Lari Lampen (MPI-PL)
 */
public class RegistryReader {

    private static final Logger logger = LogManager.getLogger(RegistryReader.class);
    private static URL registryUrl = null;

    private static final Map<String, DocumentContext> modelCache = new HashMap<>();
    
    //JsonPath configuration
    private static com.jayway.jsonpath.Configuration conf = com.jayway.jsonpath.Configuration.defaultConfiguration();

    /**
     * Create a new registry reader object.
     */
    public RegistryReader(URL url) {
        this.registryUrl = url;
        
        conf.addOptions(Option.ALWAYS_RETURN_LIST,Option.SUPPRESS_EXCEPTIONS);
    }

    private HttpURLConnection getConnection(URL url, String contentType) throws IOException {
        HttpURLConnection connection = null;
        Boolean redirect = false;

        do {
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", contentType);
            connection.connect();

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                if (status == HttpURLConnection.HTTP_MOVED_TEMP
                        || status == HttpURLConnection.HTTP_MOVED_PERM
                        || status == HttpURLConnection.HTTP_SEE_OTHER) {
                    redirect = true;
                    // get redirect url from "location" header field
                    url = new URL(connection.getHeaderField("Location"));
                    logger.debug("Center Registry redirect to URL : " + url);
                } else {
                    redirect = false;
                }
            }

        } while (redirect);
        return connection;
    }

    private HttpURLConnection getConnection(String contentType) throws IOException {
        return getConnection(registryUrl, contentType);
    }

    private String getJSONString(InputStream stream) throws IOException {
        BufferedReader rd = new BufferedReader(new InputStreamReader(stream));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) {
            sb.append(line);
        }
        rd.close();
        return sb.toString();
    }

    private DocumentContext getModel(String model) throws IOException {
        DocumentContext res = null;
        if (modelCache.containsKey(model)) {
            res = modelCache.get(model);
        } else {
            URL regUrl = new URL(registryUrl.toString() + (registryUrl.toString().endsWith("/") ? "" : "/") + model);
            HttpURLConnection connection = getConnection(regUrl, "application/json");
            String jsonString = getJSONString(connection.getInputStream());
            res = JsonPath.using(conf).parse(jsonString);
            modelCache.put(model,res);
        }
        return res;
    }

    /**
     * Get a list of all OAI-PMH endpoint URLs defined in the specified
     * registry.
     *
     * @return list of all OAI-PMH endpoint URLs
     */
    public List<String> getEndpoints() throws IOException {
        DocumentContext model = getModel("OAIPMHEndpoint");
        List<String> endpoints = model.read("$..uri");
        logger.info("Found " + endpoints.size() + " endpoints");
        return endpoints;
    }

    private List<Object> getEndpoint(String url) throws IOException {
        DocumentContext model = getModel("OAIPMHEndpoint");
        Filter uriFilter = filter(where("uri").is(url));
        return model.read("$.fields[?]",uriFilter);
    }

    public String endpointMapping(String endpointUrl, String endpointName) throws IOException {
        String directoryName = Util.toFileFormat(endpointName).replaceAll("/", "");
        
        Filter provFilter = filter(where("@.fields.uri").is(endpointUrl));
        List<Integer> iList = (List<Integer>)getModel("OAIPMHEndpoint").read("$.[?].fields.centre", provFilter);
        Integer centreKey = iList.size()>0? iList.get(0):null;

        String centreName = "";
        String nationalProject = "";

        if (centreKey != null) {
            Filter centreFilter = filter(where("pk").is(centreKey));
            List<String> sList = (List<String>)getModel("Centre").read("$[?].fields.name", centreFilter);
            centreName = sList.size()>0? sList.get(0):"";

            iList = (List<Integer>)getModel("Centre").read("$[?].fields.consortium", centreFilter);
            Integer consortiumKey = iList.size()>0? iList.get(0):null;
        
            if (consortiumKey != null) {
                Filter consortiumFilter = filter(where("pk").is(consortiumKey));
                sList = (List<String>)getModel("Consortium").read("$[?].fields.name", consortiumFilter);
                nationalProject = sList.size()>0? sList.get(0):"";
            }
        }
        
        return String.format("\"%s\",\"%s\",\"%s\",\"%s\"", endpointUrl.replaceAll("\"", "\"\""), directoryName.replaceAll("\"", "\"\""), centreName.replaceAll("\"", "\"\""), nationalProject.replaceAll("\"", "\"\""));
    }

    public Map<String, Collection<CentreRegistrySetDefinition>> getEndPointOaiPmhSetMap() {
        final Map<String, Collection<CentreRegistrySetDefinition>> map = new HashMap<>();
        try {
            final List<String> provUrls = getEndpoints();

            List<String> sList = null;
            for (String provUrl : provUrls) {
                Set<CentreRegistrySetDefinition> setdef = new HashSet<>();
                //JsonPath-> $[?(@.fields.uri=='http://www.phonetik.uni-muenchen.de/cgi-bin/BASRepository/oaipmh/oai.pl')].fields.oai_pmh_sets
                Filter provFilter = filter(where("@.fields.uri").is(provUrl));                
                List<net.minidev.json.JSONArray> s = (List<net.minidev.json.JSONArray>)getModel("OAIPMHEndpoint").read("$.[?].fields.oai_pmh_sets", provFilter);
                for(net.minidev.json.JSONArray set:s) {
                    for (Iterator iter = set.iterator();iter.hasNext();) {
                        Filter setFilter = filter(where("pk").is((Integer)iter.next()));
                        sList = (List<String>)getModel("OAIPMHEndpointSet").read("$[?].fields.set_spec", setFilter);
                        String setSpec = (sList.size()>0 ? sList.get(0) : null);
                        sList = (List<String>)getModel("OAIPMHEndpointSet").read("$[?].fields.set_type", setFilter);
                        String setType = (sList.size()>0 ? sList.get(0) : null);
                        if (setSpec!=null && setType!=null)
                            setdef.add(new CentreRegistrySetDefinition(setSpec, setType));
                    }
                }
                map.put(provUrl, setdef);
            }
        } catch (IOException e) {
            logger.error("Error reading from centre registry", e);
        }
        return map;
    }

}
