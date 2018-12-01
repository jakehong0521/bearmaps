import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.List;

/* Maven is used to pull in these dependencies. */
import com.google.gson.Gson;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import static spark.Spark.*;

/**
 * This MapServer class is the entry point for running the JavaSpark web server for the BearMaps
 * application project, receiving API calls, handling the API call processing, and generating
 * requested images and routes.
 * @author Alan Yao
 */
public class MapServer {
    /**
     * The root upper left/lower right longitudes and latitudes represent the bounding box of
     * the root tile, as the images in the img/ folder are scraped.
     * Longitude == x-axis; latitude == y-axis.
     */
    public static final double ROOT_ULLAT = 37.892195547244356, ROOT_ULLON = -122.2998046875,
            ROOT_LRLAT = 37.82280243352756, ROOT_LRLON = -122.2119140625;
    /** Each tile is 256x256 pixels. */
    public static final int TILE_SIZE = 256;
    /** HTTP failed response. */
    private static final int HALT_RESPONSE = 403;
    /** Route stroke information: typically roads are not more than 5px wide. */
    public static final float ROUTE_STROKE_WIDTH_PX = 5.0f;
    /** Route stroke information: Cyan with half transparency. */
    public static final Color ROUTE_STROKE_COLOR = new Color(108, 181, 230, 200);
    /** The tile images are in the IMG_ROOT folder. */
    private static final String IMG_ROOT = "img/";
    /**
     * The OSM XML file path. Downloaded from <a href="http://download.bbbike.org/osm/">here</a>
     * using custom region selection.
     **/
    private static final String OSM_DB_PATH = "berkeley.osm";
    /**
     * Each raster request to the server will have the following parameters
     * as keys in the params map accessible by,
     * i.e., params.get("ullat") inside getMapRaster(). <br>
     * ullat -> upper left corner latitude,<br> ullon -> upper left corner longitude, <br>
     * lrlat -> lower right corner latitude,<br> lrlon -> lower right corner longitude <br>
     * w -> user viewport window width in pixels,<br> h -> user viewport height in pixels.
     **/
    private static final String[] REQUIRED_RASTER_REQUEST_PARAMS = {"ullat", "ullon", "lrlat",
        "lrlon", "w", "h"};
    /**
     * Each route request to the server will have the following parameters
     * as keys in the params map.<br>
     * start_lat -> start point latitude,<br> start_lon -> start point longitude,<br>
     * end_lat -> end point latitude, <br>end_lon -> end point longitude.
     **/
    private static final String[] REQUIRED_ROUTE_REQUEST_PARAMS = {"start_lat", "start_lon",
        "end_lat", "end_lon"};
    /* Define any static variables here. Do not define any instance variables of MapServer. */
    private static GraphDB g;
    private static Trie trie = new Trie();
    private static QuadTree t = new QuadTree();

    public static Trie getTrie() {
        return trie;
    }
    /**
     * Place any initialization statements that will be run before the server main loop here.
     * Do not place it in the main function. Do not place initialization code anywhere else.
     * This is for testing purposes, and you may fail tests otherwise.
     **/
    public static void initialize() {
        g = new GraphDB(OSM_DB_PATH);
    }

    public static void main(String[] args) {    // given
        initialize();
        staticFileLocation("/page");
        /* Allow for all origin requests (since this is not an authenticated server, we do not
         * care about CSRF).  */
        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Request-Method", "*");
            response.header("Access-Control-Allow-Headers", "*");
        });

        /* Define the raster endpoint for HTTP GET requests. I use anonymous functions to define
         * the request handlers. */
        get("/raster", (req, res) -> {
            HashMap<String, Double> rasterParams =
                    getRequestParams(req, REQUIRED_RASTER_REQUEST_PARAMS);
            /* Required to have valid raster params */
            validateRequestParameters(rasterParams, REQUIRED_RASTER_REQUEST_PARAMS);
            /* Create the Map for return parameters. */
            Map<String, Object> rasteredImgParams = new HashMap<>();
            /* getMapRaster() does almost all the work for this API call */
            BufferedImage im = getMapRaster(rasterParams, rasteredImgParams);
            /* Check if we have routing parameters. */
            HashMap<String, Double> routeParams =
                    getRequestParams(req, REQUIRED_ROUTE_REQUEST_PARAMS);
            /* If we do, draw the route too. */
            if (hasRequestParameters(routeParams, REQUIRED_ROUTE_REQUEST_PARAMS)) {
                findAndDrawRoute(routeParams, rasteredImgParams, im);
            }
            /* On an image query success, add the image data to the response */
            if (rasteredImgParams.containsKey("query_success")
                    && (Boolean) rasteredImgParams.get("query_success")) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                writeJpgToStream(im, os);
                String encodedImage = Base64.getEncoder().encodeToString(os.toByteArray());
                rasteredImgParams.put("b64_encoded_image_data", encodedImage);
                os.flush();
                os.close();
            }
            /* Encode response to Json */
            Gson gson = new Gson();
            return gson.toJson(rasteredImgParams);
        });

        /* Define the API endpoint for search */
        get("/search", (req, res) -> {
            Set<String> reqParams = req.queryParams();
            String term = req.queryParams("term");
            Gson gson = new Gson();
            /* Search for actual location data. */
            if (reqParams.contains("full")) {
                List<Map<String, Object>> data = getLocations(term);
                return gson.toJson(data);
            } else {
                /* Search for prefix matching strings. */
                List<String> matches = getLocationsByPrefix(term);
                return gson.toJson(matches);
            }
        });

        /* Define map application redirect */
        get("/", (request, response) -> {
            response.redirect("/map.html", 301);
            return true;
        });
    }

    /**
     * Check if the computed parameter map matches the required parameters on length.
     */
    private static boolean hasRequestParameters(
            HashMap<String, Double> params, String[] requiredParams) {
        return params.size() == requiredParams.length;
    }

    /**
     * Validate that the computed parameters matches the required parameters.
     * If the parameters do not match, halt.
     */
    private static void validateRequestParameters(
            HashMap<String, Double> params, String[] requiredParams) {
        if (params.size() != requiredParams.length) {
            halt(HALT_RESPONSE, "Request failed - parameters missing.");
        }
    }

    /**
     * Return a parameter map of the required request parameters.
     * Requires that all input parameters are doubles.
     * @param req HTTP Request
     * @param requiredParams TestParams to validate
     * @return A populated map of input parameter to it's numerical value.
     */
    private static HashMap<String, Double> getRequestParams(
            spark.Request req, String[] requiredParams) {
        Set<String> reqParams = req.queryParams();
        HashMap<String, Double> params = new HashMap<>();
        for (String param : requiredParams) {
            if (reqParams.contains(param)) {
                try {
                    params.put(param, Double.parseDouble(req.queryParams(param)));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    halt(HALT_RESPONSE, "Incorrect parameters - provide numbers.");
                }
            }
        }
        return params;
    }

    /**
     * Write a <code>BufferedImage</code> to an <code>OutputStream</code>. The image is written as
     * a lossy JPG, but with the highest quality possible.
     * @param im Image to be written.
     * @param os Stream to be written to.
     */
    static void writeJpgToStream(BufferedImage im, OutputStream os) {   // given
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(1.0F); // Highest quality of jpg possible
        writer.setOutput(new MemoryCacheImageOutputStream(os));
        try {
            writer.write(im);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles raster API calls, queries for tiles and rasters the full image. <br>
     * <p>
     *     The rastered photo must have the following properties:
     *     <ul>
     *         <li>Has dimensions of at least w by h, where w and h are the user viewport width
     *         and height.</li>
     *         <li>The tiles collected must cover the most longitudinal distance per pixel
     *         possible, while still covering less than or equal to the amount of
     *         longitudinal distance per pixel in the query box for the user viewport size. </li>
     *         <li>Contains all tiles that intersect the query bounding box that fulfill the
     *         above condition.</li>
     *         <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     *     </ul>
     *     Additional image about the raster is returned and is to be included in the Json response.
     * </p>
     * @param inputParams Map of the HTTP GET request's query parameters - the query bounding box
     *                    and the user viewport width and height.
     * @param rasteredImageParams A map of parameters for the Json response as specified:
     * "raster_ul_lon" -> Double, the bounding upper left longitude of the rastered image <br>
     * "raster_ul_lat" -> Double, the bounding upper left latitude of the rastered image <br>
     * "raster_lr_lon" -> Double, the bounding lower right longitude of the rastered image <br>
     * "raster_lr_lat" -> Double, the bounding lower right latitude of the rastered image <br>
     * "raster_width"  -> Integer, the width of the rastered image <br>
     * "raster_height" -> Integer, the height of the rastered image <br>
     * "depth"         -> Integer, the 1-indexed quadtree depth of the nodes of the rastered image.
     * Can also be interpreted as the length of the numbers in the image string. <br>
     * "query_success" -> Boolean, whether an image was successfully rastered. <br>
     * @return a <code>BufferedImage</code>, which is the rastered result.
     * @see #REQUIRED_RASTER_REQUEST_PARAMS
     */
    public static BufferedImage getMapRaster(Map<String, Double> inputParams,
                                             Map<String, Object> rasteredImageParams) {

        ArrayList<QTreeNode> images = t.getImg(inputParams);

        int numImages = images.size();
        int depth;
        if (images.get(0).getName().equals("root")) {
            depth = 0;
        } else {
            depth = images.get(0).getName().length();
        }

        double rasterUlLon = images.get(0).getULLON();
        double rasterUlLat = images.get(0).getULLAT();
        double rasterLrLon = images.get(numImages - 1).getLRLON();
        double rasterLrLat = images.get(numImages - 1).getLRLAT();

        int xTile = ((int) Math.round(((rasterLrLon - rasterUlLon)
                / ((ROOT_LRLON - ROOT_ULLON) / Math.pow(2, depth)))));
        int yTile = numImages / xTile;

        double xTileDist = (rasterLrLon - rasterUlLon) / xTile;
        double yTileDist = (rasterUlLat - rasterLrLat) / yTile;

        rasteredImageParams.put("raster_width", xTile * 256);
        rasteredImageParams.put("raster_height", yTile * 256);
        rasteredImageParams.put("depth", depth);
        rasteredImageParams.put("raster_ul_lon", rasterUlLon);
        rasteredImageParams.put("raster_ul_lat", rasterUlLat);
        rasteredImageParams.put("raster_lr_lon", rasterLrLon);
        rasteredImageParams.put("raster_lr_lat", rasterLrLat);
        rasteredImageParams.put("query_success", true);

        BufferedImage result
                = new BufferedImage(xTile * 256, yTile * 256, BufferedImage.TYPE_INT_RGB);
        Graphics bigImage = result.getGraphics();

        for (QTreeNode image : images) {
            try {
                BufferedImage bi;
                if (image.getBufferedImage() == null) {
                    bi = ImageIO.read(new File("img/" + image.getName() + ".png"));
                    image.setImage(bi);
                } else {
                    bi = image.getBufferedImage();
                }
                int x = (int) Math.round((image.getULLON() - rasterUlLon) / xTileDist);
                int y = (int) Math.round((rasterUlLat - image.getULLAT()) / yTileDist);
                bigImage.drawImage(bi, x * 256, y * 256, null);
            } catch (IOException e) {
                System.out.println(image.getName());
            }
        }
        return result;
    }

    /**
     * Searches for the shortest route satisfying the input request parameters, and returns a
     * <code>List</code> of the route's node ids. <br>
     * The route should start from the closest node to the start point and end at the closest node
     * to the endpoint. Distance is defined as the euclidean distance between two points
     * (lon1, lat1) and (lon2, lat2).
     * If <code>im</code> is not null, draw the route onto the image by drawing lines in between
     * adjacent points in the route. The lines should be drawn using ROUTE_STROKE_COLOR,
     * ROUTE_STROKE_WIDTH_PX, BasicStroke.CAP_ROUND and BasicStroke.JOIN_ROUND.
     * @param routeParams Params collected from the API call. Members are as
     *                    described in REQUIRED_ROUTE_REQUEST_PARAMS.
     * @param rasterImageParams parameters returned from the image rastering.
     * @param im The rastered map image to be drawn on.
     * @return A List of node ids from the start of the route to the end.
     */
    public static List<Long> findAndDrawRoute(Map<String, Double> routeParams,
                                              Map<String, Object> rasterImageParams,
                                              BufferedImage im) {
        Double startLon = routeParams.get("start_lon");
        Double startLat = routeParams.get("start_lat");
        Double endLon = routeParams.get("end_lon");
        Double endLat = routeParams.get("end_lat");
        String[] startEnd = findStartAndEnd(startLon, startLat, endLon, endLat);
        GraphNode startNode = MapDBHandler.allNodes().get(startEnd[0]);
        GraphNode endNode = MapDBHandler.allNodes().get(startEnd[1]);
        Long endId = endNode.id();

        PriorityQueue<HashMap<String, Object>> fringe =
                new PriorityQueue<>((o1, o2) ->
                        ((Double) o1.get("dist")).compareTo((Double) o2.get("dist")));
        HashMap<Long, HashMap<String, Object>> distance = new HashMap<>();
        HashMap<String, Object> startNodeMap = new HashMap<>();
        startNodeMap.put("node", startNode);
        startNodeMap.put("dist", 0.0);
        fringe.add(startNodeMap);
        distance.put(startNode.id(), startNodeMap);

        while (!fringe.isEmpty()) {
            GraphNode next = (GraphNode) fringe.poll().get("node");
            if (next.id().equals(endId)) {
                break;
            }

            Double dist = (Double) distance.get(next.id()).get("dist");
            ArrayList<GraphNode> neighbors = next.getNeighbor();
            for (GraphNode node : neighbors) {
                if (!distance.containsKey(node.id())) {
                    HashMap<String, Object> currMap = new HashMap<>();
                    currMap.put("node", node);
                    currMap.put("dist", dist
                            + h(node.getLon(), node.getLat(), next.getLon(), next.getLat()));
                    currMap.put("prev", next.id());
                    fringe.add(currMap);
                    distance.put(node.id(), currMap);
                } else {
                    if (((Double) distance.get(node.id()).get("dist"))
                            > dist + h(node.getLon(), node.getLat(),
                            next.getLon(), next.getLat())) {
                        distance.get(node.id()).put("dist", dist
                                + h(node.getLon(), node.getLat(), next.getLon(), next.getLat()));
                        distance.get(node.id()).put("prev", next.id());
                    }
                }
            }
        }

        ArrayList<Long> route = new ArrayList<>();
        route.add(endNode.id());
        while (!route.get(0).equals(startNode.id())) {
            route.add(0, (Long) distance.get(route.get(0)).get("prev"));
        }

        if (route.size() != 0 && im != null) {
            Graphics graphics = im.getGraphics();
            int column = (int) rasterImageParams.get("raster_width");
            int row = (int) rasterImageParams.get("raster_height");
            double rasterUllon = (Double) rasterImageParams.get("raster_ul_lon");
            double rasterUllat = (Double) rasterImageParams.get("raster_ul_lat");
            double rasterLrlon = (Double) rasterImageParams.get("raster_lr_lon");
            double rasterLrlat = (Double) rasterImageParams.get("raster_lr_lat");

            for (int i = 0; i < route.size() - 1; i++) {
                ((Graphics2D) graphics).setStroke(
                        new BasicStroke(MapServer.ROUTE_STROKE_WIDTH_PX,
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.setColor(ROUTE_STROKE_COLOR);
                GraphNode fromNode = MapDBHandler.allNodes().get(route.get(i).toString());
                GraphNode toNode = MapDBHandler.allNodes().get(route.get(i + 1).toString());

                int x1 = (int) (Math.round(column)
                        * ((fromNode.getLon() - rasterUllon) / (rasterLrlon - rasterUllon)));
                int y1 = (int) (Math.round(row)
                        * ((rasterUllat - fromNode.getLat()) / (rasterUllat - rasterLrlat)));
                int x2 = (int) (Math.round(column)
                        * ((toNode.getLon() - rasterUllon) / (rasterLrlon - rasterUllon)));
                int y2 = (int) (Math.round(row)
                        * ((rasterUllat - toNode.getLat()) / (rasterUllat - rasterLrlat)));
                graphics.drawLine(x1, y1, x2, y2);
            }
        }

//        try {
//            BufferedImage bi = im;
//            File outputfile = new File("saved.png");
//            ImageIO.write(bi, "png", outputfile);
//        } catch (IOException e) {
//

        return route;
    }

    public static String[] findStartAndEnd(
            Double startLon, Double startLat, Double endLon, Double endLat) {
        Double startDis = Double.POSITIVE_INFINITY;
        Double endDis = Double.POSITIVE_INFINITY;
        String startId = "";
        String endId = "";
        Map<String, GraphNode> nodes = MapDBHandler.allNodes();

        for (String id: nodes.keySet()) {
            if (startDis > h(startLon, startLat, nodes.get(id).getLon(), nodes.get(id).getLat())) {
                startDis = h(startLon, startLat, nodes.get(id).getLon(), nodes.get(id).getLat());
                startId = id;
            }
            if (endDis > h(endLon, endLat, nodes.get(id).getLon(), nodes.get(id).getLat())) {
                endDis = h(endLon, endLat, nodes.get(id).getLon(), nodes.get(id).getLat());
                endId = id;
            }
        }

        return new String[]{startId, endId};
    }

    public static double h(double lon1, double lat1, double lon2, double lat2) {
        return Math.sqrt(Math.pow((lon2 - lon1), 2) + Math.pow((lat2 - lat1), 2));
    }

    /**
     * In linear time, collect all the names of OSM locations that prefix-match the query string.
     * @param prefix Prefix string to be searched for. Could be any case, with our without
     *               punctuation.
     * @return A <code>List</code> of the full names of locations whose cleaned name matches the
     * cleaned <code>prefix</code>.
     */
    public static List<String> getLocationsByPrefix(String prefix) {    // for proj3
        List<GraphNode> list = trie.getWordsWithPrefix(GraphDB.cleanString(prefix));
        ArrayList<String> collecting = new ArrayList<>();
        for (GraphNode node: list) {
            collecting.add(node.getName());
        }
        return collecting;
    }

    /**
     * Collect all locations that match a cleaned <code>locationName</code>, and return
     * information about each node that matches.
     * @param locationName A full name of a location searched for.
     * @return A list of locations whose cleaned name matches the
     * cleaned <code>locationName</code>, and each location is a map of parameters for the Json
     * response as specified: <br>
     * "lat" -> Number, The latitude of the node. <br>
     * "lon" -> Number, The longitude of the node. <br>
     * "name" -> String, The actual name of the node. <br>
     * "id" -> Number, The id of the node. <br>
     */
    public static List<Map<String, Object>> getLocations(String locationName) { // for proj3
        List<GraphNode> collecting = trie.getWordsWithPrefix(locationName);
        LinkedList<Map<String, Object>> returning = new LinkedList<>();
        for (GraphNode curr: collecting) {
            HashMap<String, Object> node = new HashMap<>();
            node.put("lat", curr.getLat());
            node.put("lon", curr.getLon());
            node.put("name", curr.getName());
            node.put("id", curr.id());
            returning.add(node);
        }
        return returning;
    }
}
