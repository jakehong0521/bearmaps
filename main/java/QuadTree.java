import java.util.ArrayList;
import java.util.Map;

/**
 * Created by JunSeong on 7/16/2016.
 */

public class QuadTree {

    private static QTreeNode root;

    private ArrayList<QTreeNode> imgForQuery = new ArrayList<>();

    public QuadTree() {
        root = new QTreeNode("root", MapServer.ROOT_ULLON, MapServer.ROOT_ULLAT,
                MapServer.ROOT_LRLON, MapServer.ROOT_LRLAT);
    }

    public ArrayList<QTreeNode> getImg(Map<String, Double> queryParams) {
        imgForQuery = new ArrayList<>();
        addToBuffer(root, queryParams);
        return imgForQuery;
    }

    public void addToBuffer(QTreeNode tree, Map<String, Double> queryParams) {
        if (tree.containsQuery(queryParams)) {
            if (tree.finerThanQuery(queryParams) || tree.getName().length() == 7) {
                imgForQuery.add(tree);
            } else {
                if (!tree.hasChildren()) {
                    tree.createChildren();
                }
                addToBuffer(tree.getChild1(), queryParams);
                addToBuffer(tree.getChild2(), queryParams);
                addToBuffer(tree.getChild3(), queryParams);
                addToBuffer(tree.getChild4(), queryParams);
            }
        }
    }
}
