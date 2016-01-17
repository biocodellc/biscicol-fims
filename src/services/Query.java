package services;

import biocode.fims.bcid.ProjectMinter;
import biocode.fims.config.ConfigurationFileFetcher;
import biocode.fims.digester.Attribute;
import biocode.fims.digester.Mapping;
import biocode.fims.fimsExceptions.FimsRuntimeException;
import biocode.fims.fuseki.query.FimsFilterCondition;
import biocode.fims.fuseki.query.FimsQueryBuilder;
import biocode.fims.rest.FimsService;
import biocode.fims.run.Process;
import org.apache.commons.digester3.Digester;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.*;

/**
 * Query interface for Biocode-fims expedition
 */
@Path("/projects/query")
public class Query extends FimsService {
    private static Logger logger = LoggerFactory.getLogger(Query.class);
    private File configFile;

    /**
     * Return JSON for a graph query as POST
     * <p/>
     * filter parameters are of the form:
     * name={URI} value={filter value}
     *
     * @return
     */
    @POST
    @Path("/json/")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response queryJsonAsPOST(
            MultivaluedMap<String, String> form) {

        // Build the query, etc..
        FimsQueryBuilder q = POSTQueryResult(form);

        // Run the query, passing in a format and returning the location of the output file
        File file = new File(q.run("json"));

        // Wrie the file to a String and return it
        String response = readFile(file.getAbsolutePath());

        // Return response
        if (response == null) {
            return Response.status(204).build();
        } else {
            return Response.ok(response).build();
        }
    }

    /**
     * Return JSON for a graph query.
     *
     * @param graphs indicate a comma-separated list of graphs, or all
     * @return
     */
    @GET
    @Path("/json/")
    @Produces(MediaType.APPLICATION_JSON)
    public Response queryJson(
            @QueryParam("graphs") String graphs,
            @QueryParam("project_id") Integer project_id,
            @QueryParam("filter") String filter) {

        File file = GETQueryResult(graphs, project_id, filter, "json");

        String response = readFile(file.getAbsolutePath());

        // Return response
        if (response == null) {
            return Response.status(204).build();
        } else {
            return Response.ok(response).build();
        }
    }

    /**
     * Return KML for a graph query using POST
     * * <p/>
     * filter parameters are of the form:
     * name={URI} value={filter value}
     *
     * @return
     */
    @POST
    @Path("/kml/")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces("application/vnd.google-earth.kml+xml")
    public Response queryKml(
            MultivaluedMap<String, String> form) {

        // Build the query, etc..
        FimsQueryBuilder q = POSTQueryResult(form);

        // Run the query, passing in a format and returning the location of the output file
        File file = new File(q.run("kml"));

        // Return file to client
        Response.ResponseBuilder response = Response.ok((Object) file);
        response.header("Content-Disposition",
                "attachment; filename=biocode-fims-output.kml");

        // Return response
        if (response == null) {
            return Response.status(204).build();
        } else {
            return response.build();
        }
    }


    /**
     * Return Tab delimited data for a graph query.  The GET query runs a simple FILTER query for any term
     *
     * @param graphs indicate a comma-separated list of graphs, or all
     * @return
     */
    @GET
    @Path("/tab/")
    public Response queryTab(
            @QueryParam("graphs") String graphs,
            @QueryParam("projectId") Integer projectId,
            @QueryParam("filter") String filter) {

        File file = GETQueryResult(graphs, projectId, filter, "tab");

        // Return file to client
        Response.ResponseBuilder response = Response.ok((Object) file);
        response.header("Content-Disposition",
                "attachment; filename=biocode-fims-output.txt");

        // Return response
        if (response == null) {
            return Response.status(204).build();
        } else {
            return response.build();
        }
    }

    /**
     * Return Excel for a graph query.  The GET query runs a simple FILTER query for any term
     *
     * @param graphs indicate a comma-separated list of graphs, or all
     * @return
     */
    @GET
    @Path("/excel/")
    @Produces("application/vnd.ms-excel")
    public Response queryExcel(
            @QueryParam("graphs") String graphs,
            @QueryParam("projectId") Integer projectId,
            @QueryParam("filter") String filter) {

        File file = GETQueryResult(graphs, projectId, filter, "excel");

        // Return file to client
        Response.ResponseBuilder response = Response.ok((Object) file);
        response.header("Content-Disposition",
                "attachment; filename=biocode-fims-output.xlsx");

        // Return response
        if (response == null) {
            return Response.status(204).build();
        } else {
            return response.build();
        }
    }

    /**
     * Return Excel for a graph query using POST
     * * <p/>
     * filter parameters are of the form:
     * name={URI} value={filter value}
     *
     *
     * @return
     */
    @POST
    @Path("/excel/")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces("application/vnd.ms-excel")
    public Response queryExcel(
            MultivaluedMap<String, String> form) {

        // Build the query, etc..
        FimsQueryBuilder q = POSTQueryResult(form);

        // Run the query, passing in a format and returning the location of the output file
        File file = new File(q.run("excel"));

        // Return file to client
        Response.ResponseBuilder response = Response.ok((Object) file);
        response.header("Content-Disposition",
                "attachment; filename=biocode-fims-output.xls");

        // Return response
        if (response == null) {
            return Response.status(204).build();
        } else {
            return response.build();
        }
    }

    /**
     * Get the POST query result as a file
     *
     * @return
     */
    private FimsQueryBuilder POSTQueryResult(MultivaluedMap<String, String> form) {
        Iterator entries = form.entrySet().iterator();
        String[] graphs = null;
        Integer projectId = null;

        HashMap<String, String> filterMap = new HashMap<String, String>();
        ArrayList<FimsFilterCondition> filterConditionArrayList = new ArrayList<FimsFilterCondition>();

        while (entries.hasNext()) {
            Map.Entry thisEntry = (Map.Entry) entries.next();
            String key = (String) thisEntry.getKey();
            // Values come over as a linked list
            LinkedList value = (LinkedList) thisEntry.getValue();
            if (key.equalsIgnoreCase("graphs")) {
                Object[] valueArray = value.toArray();
                graphs = Arrays.copyOf(valueArray, valueArray.length, String[].class);
            } else if (key.equalsIgnoreCase("projectId")) {
                projectId = Integer.parseInt((String) value.get(0));
                System.out.println("projectId_val=" + (String)value.get(0) );
                System.out.println("projectId_int=" + projectId );
            } else if (key.equalsIgnoreCase("boolean")) {
                /// AND|OR
                //projectId = Integer.parseInt((String) value.get(0));
            } else if (key.equalsIgnoreCase("submit")) {
                // do nothing with this
            } else {
                String v = (String) value.get(0);// only expect 1 value here
                filterMap.put(key, v);
            }
        }

        // Make sure graphs and projectId are set
        if (graphs != null && graphs.length < 1 && projectId != null) {
            throw new FimsRuntimeException("ERROR: incomplete arguments", 400);
        }

        if (graphs[0].equalsIgnoreCase("all")) {
            graphs = getAllGraphs(projectId);
        }

        // Create a process object here so we can look at uri/column values
        Process process = getProcess(projectId);

        // Build the Query
        FimsQueryBuilder q = new FimsQueryBuilder(process, graphs, uploadPath());

        // Loop the filterMap entries and build the filterConditionArrayList
        Iterator it = filterMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry) it.next();
            FimsFilterCondition f = parsePOSTFilter((String) pairs.getKey(), (String) pairs.getValue());
            if (f != null)
                filterConditionArrayList.add(f);
            it.remove();
        }

        // Add our filter conditions
        if (filterConditionArrayList != null && filterConditionArrayList.size() > 0)
            q.addFilter(filterConditionArrayList);

        return q;
    }

    /**
     * Get the query result as a file
     *
     * @param graphs
     * @param projectId
     * @param filter
     * @param format
     * @return
     */
    private File GETQueryResult(String graphs, Integer projectId, String filter, String format) {
        String[] graphsArray;

        try {
            graphs = URLDecoder.decode(graphs, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.warn("UnsupportedEncodingException", e);
        }

        if (graphs.equalsIgnoreCase("all")) {
            graphsArray = getAllGraphs(projectId);
        } else {
            graphsArray = graphs.split(",");
        }

        Process process = getProcess(projectId);

        // Parse the GET filter
        FimsFilterCondition filterCondition = parseGETFilter(filter);

        if (filterCondition != null) {
            // Create a filter statement
            ArrayList<FimsFilterCondition> arrayList = new ArrayList<FimsFilterCondition>();
            arrayList.add(filterCondition);

            // Run the query
            // Build the Query Object by passing this object and an array of graph objects, separated by commas
            FimsQueryBuilder q = new FimsQueryBuilder(process, graphsArray, uploadPath());
            // Add our filter conditions
            q.addFilter(arrayList);
            // Run the query, passing in a format and returning the location of the output file
            return new File(q.run(format));
        } else {
            // Run the query
            // Build the Query Object by passing this object and an array of graph objects, separated by commas
            FimsQueryBuilder q = new FimsQueryBuilder(process, graphsArray, uploadPath());
            // Run the query, passing in a format and returning the location of the output file
            return new File(q.run(format));
        }
    }

    private Process getProcess(Integer projectId) {
        configFile = new ConfigurationFileFetcher(projectId, uploadPath(), true).getOutputFile();

        // Create a process object
        Process p = new Process(
                projectId,
                configFile
        );
        return p;
    }

    private String[] getAllGraphs(Integer projectId) {
        List<String> graphsList = new ArrayList<String>();

        ProjectMinter project= new ProjectMinter();

        JSONArray graphs = project.getLatestGraphs(projectId, username);
        project.close();
        Iterator it = graphs.iterator();

        while (it.hasNext()) {
            JSONObject obj = (JSONObject) it.next();
            graphsList.add((String) obj.get("graph"));
        }

        return graphsList.toArray(new String[graphsList.size()]);
    }

    /**
     * Read a file and return it as a String... meant to be used within this class only
     *
     * @param file
     * @return
     */
    private String readFile(String file) {
        FileReader fr;
        try {
            fr = new FileReader(file);
        } catch (FileNotFoundException e) {
            throw new FimsRuntimeException(500, e);
        }
        BufferedReader reader = new BufferedReader(fr);
        String line = null;
        StringBuilder stringBuilder = new StringBuilder();
        String ls = System.getProperty("line.separator");
        try {
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append(ls);
            }
        } catch (IOException e) {
            throw new FimsRuntimeException(500, e);
        }
        return stringBuilder.toString();
    }

    /**
     * Parse the GET filter string smartly.  Maps what looks like a column to a URI using the configuration file
     * and if it looks like a URI then creates a URI straight from the key.
     *
     * @param key
     * @param value
     * @return
     */
    private FimsFilterCondition parsePOSTFilter(String key, String value) {
        URI uri = null;

        if (key == null || key.equals("") || value == null || value.equals(""))
            return null;

        // this is a predicate/URI query
        if (key.contains(":")) {
            try {
                uri = new URI(key);
            } catch (URISyntaxException e) {
                throw new FimsRuntimeException(500, e);
            }
        } else {
            Mapping mapping = new Mapping();
            mapping.addMappingRules(new Digester(), configFile);
            ArrayList<Attribute> attributeArrayList = mapping.getAllAttributes(mapping.getDefaultSheetName());
            uri = mapping.lookupColumn(key, attributeArrayList);
        }
        return new FimsFilterCondition(uri, value, FimsFilterCondition.AND);

    }

    /**
     * Parse the GET filter string smartly.  This looks for either column Names or URI Properties, and
     * if it finds a column name maps to a URI Property.  Values are assumed to be last element past a semicolon ALWAYS.
     *
     * @param filter
     * @return
     */
    private FimsFilterCondition parseGETFilter(String filter) {
        Mapping mapping = new Mapping();
        mapping.addMappingRules(new Digester(), configFile);

        String delimiter = ":";
        URI uri = null;
        URLDecoder decoder = new URLDecoder();

        if (filter == null)
            return null;

        String[] filterSplit = filter.split(":");

        // Get the value we're looking for
        Integer lastValue = filterSplit.length - 1;
        try {
            String value = decoder.decode(filterSplit[lastValue], "UTF8").toString();

            // Build the predicate.
            if (filterSplit.length != lastValue) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < lastValue; i++) {
                    if (i > 0) {
                        sb.append(delimiter);
                    }
                    sb.append(decoder.decode(filterSplit[i], "UTF8").toString());
                }

                // re-assemble the string
                String key = sb.toString();

                // If the key contains a semicolon, then assume it is a URI
                if (key.contains(":")) {
                    uri = new URI(key);
                }
                // If there is no semicolon here then assume the user passed in a column name
                else {
                    ArrayList<Attribute> attributeArrayList = mapping.getAllAttributes(mapping.getDefaultSheetName());
                    uri = mapping.lookupColumn(key, attributeArrayList);
                }
            }
            return new FimsFilterCondition(uri, value, FimsFilterCondition.AND);
        } catch (UnsupportedEncodingException e) {
            throw new FimsRuntimeException(500, e);
        } catch (URISyntaxException e) {
            throw new FimsRuntimeException(500, e);
        }
    }
}

