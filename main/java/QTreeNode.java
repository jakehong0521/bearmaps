import java.awt.image.BufferedImage;
import java.awt.*;
import java.util.Map;

public class QTreeNode {
    private QTreeNode child1;
    private QTreeNode child2;
    private QTreeNode child3;
    private QTreeNode child4;

    private BufferedImage img;

    private double width = 256.0;
    private double lonPerPx;

    private String name;

    private double ullon, ullat, lrlon, lrlat;

    public QTreeNode(String name, double ullon, double ullat, double lrlon, double lrlat) {
        this.name = name;
        this.ullon = ullon;
        this.lrlon = lrlon;
        this.ullat = ullat;
        this.lrlat = lrlat;
        lonPerPx = (lrlon - ullon) / width;
    }

    public void setImage(BufferedImage bi) {
        this.img = bi;
    }

    public BufferedImage getBufferedImage() {
        return img;
    }

    public String getName() {
        return name;
    }

    public QTreeNode getChild1() {
        return child1;
    }

    public QTreeNode getChild2() {
        return child2;
    }

    public QTreeNode getChild3() {
        return child3;
    }

    public QTreeNode getChild4() {
        return child4;
    }

    public boolean hasChildren() {
        return (child1 != null);
    }

    public double getULLON() {
        return ullon;
    }

    public double getULLAT() {
        return ullat;
    }

    public double getLRLON() {
        return lrlon;
    }

    public double getLRLAT() {
        return lrlat;
    }

    public boolean containsQuery(Map<String, Double> queryParams) {
        if ((queryParams.get("ullon") <= ullon && ullon <= queryParams.get("lrlon"))
                || (queryParams.get("ullon") <= lrlon && lrlon <= queryParams.get("lrlon"))) {
            if ((queryParams.get("lrlat") <= ullat && ullat <= queryParams.get("ullat"))
                    || (queryParams.get("lrlat") <= lrlat && lrlat <= queryParams.get("ullat"))) {
                return true;
            }
        }
        if ((ullon <= queryParams.get("ullon") && queryParams.get("ullon") <= lrlon)
                || (ullon <= queryParams.get("lrlon") && queryParams.get("lrlon") <= lrlon)) {
            if ((lrlat <= queryParams.get("ullat") && queryParams.get("ullat") <= ullat)
                    || (lrlat <= queryParams.get("lrlat") && queryParams.get("lrlat") <= ullat)) {
                return true;
            }
        }
        if ((ullon <= queryParams.get("ullon") && queryParams.get("ullon") <= lrlon)
                || (ullon <= queryParams.get("lrlon") && queryParams.get("lrlon") <= lrlon)) {
            if ((queryParams.get("lrlat") <= ullat && ullat <= queryParams.get("ullat"))
                    || (queryParams.get("lrlat") <= lrlat && lrlat <= queryParams.get("ullat"))) {
                return true;
            }
        }
        if ((queryParams.get("ullon") <= ullon && ullon <= queryParams.get("lrlon"))
                || (queryParams.get("ullon") <= lrlon && lrlon <= queryParams.get("lrlon"))) {
            if ((lrlat <= queryParams.get("ullat") && queryParams.get("ullat") <= ullat)
                    || (lrlat <= queryParams.get("lrlat") && queryParams.get("lrlat") <= ullat)) {
                return true;
            }
        }
        return false;
    }

    public boolean finerThanQuery(Map<String, Double> queryParams) {
        return (((queryParams.get("lrlon")
                -  queryParams.get("ullon"))
                / queryParams.get("w"))
                > lonPerPx);
    }

    public void createChildren() {
        if (name.equals("root")) {
            name = "";
        }
        child1 = new QTreeNode(name + "1",
                ullon, ullat, ullon + (lrlon - ullon) / 2, lrlat + (ullat - lrlat) / 2);
        child2 = new QTreeNode(name + "2",
                ullon + (lrlon - ullon) / 2, ullat, lrlon, lrlat + (ullat - lrlat) / 2);
        child3 = new QTreeNode(name + "3",
                ullon, lrlat + (ullat - lrlat) / 2, ullon + (lrlon - ullon) / 2, lrlat);
        child4 = new QTreeNode(name + "4",
                ullon + (lrlon - ullon) / 2, lrlat + (ullat - lrlat) / 2, lrlon, lrlat);
        if (name.equals("")) {
            name = "root";
        }
    }
}
