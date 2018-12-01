import java.util.ArrayList;

/**
 * Created by JunSeong on 8/5/2016.
 */
public class GraphNode {
    private Long id;
    private String name;
    private Double lon;
    private Double lat;
    private ArrayList<GraphNode> neighbor;

    public GraphNode(Long id, Double lon, Double lat) {
        this(id, null, lon, lat);
    }

    public GraphNode(Long id, String name, Double lon, Double lat) {
        this.id = id;
        this.name = name;
        this.lon = lon;
        this.lat = lat;
        this.neighbor = new ArrayList<>();
    }

    public ArrayList<GraphNode> getNeighbor() {
        return neighbor;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public Double getLon() {
        return lon;
    }

    public Double getLat() {
        return lat;
    }

    public Long id() {
        return this.id;
    }

    public static void addItTo(GraphNode neighbor, GraphNode g) {
        if (!g.neighbor.contains(neighbor)) {
            g.neighbor.add(neighbor);
        }
    }

}
