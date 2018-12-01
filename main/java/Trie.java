import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by JunSeong on 8/5/2016.
 */
public class Trie {
    private HashMap<Character, TrieNode> myStartingLetters;

    public Trie() {
        myStartingLetters = new HashMap<>();
    }

    public void addNode(GraphNode node) {
        String cleaned = GraphDB.cleanString(node.getName());
        if (cleaned.isEmpty()) {
            cleaned = node.getName();
        }
        if (!myStartingLetters.containsKey(cleaned.charAt(0))) {
            myStartingLetters.put(cleaned.charAt(0), new TrieNode());
        }
        TrieNode curr = myStartingLetters.get(cleaned.charAt(0));
        for (int i = 1; i < cleaned.length(); i++) {
            if (!curr.nextLetters.containsKey(cleaned.charAt(i))) {
                curr.nextLetters.put(cleaned.charAt(i), new TrieNode());
            }
            curr = curr.nextLetters.get(cleaned.charAt(i));
        }
        curr.myNode = node;
    }

    public ArrayList<GraphNode> getWordsWithPrefix(String prefix) {
        ArrayList<GraphNode> words = new ArrayList<>();
        String cleaned = GraphDB.cleanString(prefix);
        if (cleaned.isEmpty()) {
            cleaned = prefix;
        }
        TrieNode curr;
        if (!myStartingLetters.containsKey(cleaned.charAt(0))) {
            return words;
        } else {
            curr = myStartingLetters.get(cleaned.charAt(0));
        }
        for (int i = 1; i < cleaned.length(); i++) {
            if (!curr.nextLetters.containsKey(cleaned.charAt(i))) {
                return words;
            }
            curr = curr.nextLetters.get(cleaned.charAt(i));
        }
        getWordsHelper(curr, words);
        return words;
    }

    public static void getWordsHelper(TrieNode t, ArrayList<GraphNode> l) {
        if (t.myNode != null) {
            l.add(t.myNode);
        }
        if (t.nextLetters.keySet().size() > 0) {
            for (char key: t.nextLetters.keySet()) {
                getWordsHelper(t.nextLetters.get(key), l);
            }
        }
    }

    private class TrieNode {
        private HashMap<Character, TrieNode> nextLetters;
        private GraphNode myNode;

        TrieNode() {
            nextLetters = new HashMap<>();
        }
    }
}
