/**
 * @author Joshua Kissoon
 * @created 20140215
 * @desc A bucket in the Kademlia routing table
 */
package kademlia.routing;

import java.util.ArrayList;
import java.util.HashMap;
import kademlia.node.Node;
import kademlia.node.NodeId;

public class KadBucket implements Bucket
{

    private final int depth;
    private final HashMap<NodeId, Node> nodes;

    
    {
        nodes = new HashMap<>();
    }

    /**
     * @param depth How deep in the routing tree is this bucket
     */
    public KadBucket(int depth)
    {
        this.depth = depth;
    }

    @Override
    public void insert(Node n)
    {
        /*@todo Check if the bucket is filled already and handle this */
        /* Check if the contact is already in the bucket */
        if (this.nodes.containsKey(n.getNodeId()))
        {
            /* @todo If it is, then move it to the front */
            /* @todo Possibly use a doubly linked list instead of an ArrayList */
        }
        else
        {
            //System.out.println("Adding new node - " + n.getNodeId() + " to bucket depth: " + this.depth);
            nodes.put(n.getNodeId(), n);
            //System.out.println(this);
        }
    }

    /**
     * Checks if this bucket contain a node
     *
     * @param n The node to check for
     *
     * @return boolean
     */
    public boolean containNode(Node n)
    {
        return this.nodes.containsKey(n.getNodeId());
    }

    /**
     * Remove a node from this bucket
     *
     * @param n The node to remove
     */
    public void removeNode(Node n)
    {
        this.nodes.remove(n.getNodeId());
    }

    public int numNodes()
    {
        return this.nodes.size();
    }

    public int getDepth()
    {
        return this.depth;
    }

    @Override
    public void markDead(Node n)
    {
        this.nodes.remove(n.getNodeId());
    }

    public ArrayList<Node> getNodes()
    {
        return new ArrayList<>(this.nodes.values());
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("Bucket at depth: ");
        sb.append(this.depth);
        sb.append("\n Nodes: \n");
        for (Node n : this.nodes.values())
        {
            sb.append("Node: ");
            sb.append(n.getNodeId().toString());
            sb.append("\n");
        }

        return sb.toString();
    }
}
