/**
 * @author Joshua Kissoon
 * @created 20140219
 * @desc Finds the K closest nodes to a specified identifier
 * The algorithm terminates when it has gotten responses from the K closest nodes it has seen.
 * Nodes that fail to respond are removed from consideration
 */
package kademlia.operation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import kademlia.core.Configuration;
import kademlia.core.KadServer;
import kademlia.exceptions.RoutingException;
import kademlia.message.Message;
import kademlia.message.NodeLookupMessage;
import kademlia.node.Node;
import kademlia.node.NodeId;

public class NodeLookupOperation implements Operation, Receiver
{

    /* Constants */
    private static final Byte UNASKED = new Byte((byte) 0x00);
    private static final Byte AWAITING = new Byte((byte) 0x01);
    private static final Byte ASKED = new Byte((byte) 0x02);
    private static final Byte FAILED = new Byte((byte) 0x03);

    private final KadServer server;
    private final Node localNode;
    private final NodeId lookupId;

    private boolean error;

    private final Message lookupMessage;        // Message sent to each peer
    private final SortedMap<Node, Byte> nodes;

    /* Tracks messages in transit and awaiting reply */
    private final HashMap<Integer, Node> messagesTransiting;

    /* Used to sort nodes */
    private final Comparator comparator;

    
    {
        messagesTransiting = new HashMap<>();
    }

    /**
     * @param server    KadServer used for communication
     * @param localNode The local node making the communication
     * @param lookupId  The ID for which to find nodes close to
     */
    public NodeLookupOperation(KadServer server, Node localNode, NodeId lookupId)
    {
        this.server = server;
        this.localNode = localNode;
        this.lookupId = lookupId;

        this.lookupMessage = new NodeLookupMessage(localNode, lookupId);

        /**
         * We initialize a TreeMap to store nodes.
         * This map will be sorted by which nodes are closest to the lookupId
         */
        this.comparator = new Node.DistanceComparator(lookupId);
        this.nodes = new TreeMap(this.comparator);
    }

    /**
     * @return A list containing the K closest nodes to the lookupId provided
     *
     * @throws java.io.IOException
     * @throws kademlia.exceptions.RoutingException
     */
    @Override
    public synchronized ArrayList<Node> execute() throws IOException, RoutingException
    {
        try
        {
            error = true;

            /* Set the local node as already asked */
            nodes.put(this.localNode, ASKED);

            this.addNodes(this.localNode.getRoutingTable().getAllNodes());

            if (!this.askNodesorFinish())
            {
                /* If we haven't finished as yet, wait a while */
                wait(Configuration.OPERATION_TIMEOUT);

                /* If we still haven't received any responses by then, do a routing timeout */
                if (error)
                {
                    throw new RoutingException("Lookup Timeout.");
                }
            }

            /* So we have finished, lets return the closest nodes */
            return this.closestNodes(ASKED);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add nodes from this list to the set of nodes to lookup
     *
     * @param list The list from which to add nodes
     */
    public void addNodes(List<Node> list)
    {
        for (Node o : list)
        {
            /* If this node is not in the list, add the node */
            if (!nodes.containsKey(o))
            {
                nodes.put(o, UNASKED);
            }
        }
    }

    /**
     * Asks some of the K closest nodes seen but not yet queried.
     * Assures that no more than Configuration.CONCURRENCY messages are in transit at a time
     *
     * This method should be called every time a reply is received or a timeout occurs.
     *
     * If all K closest nodes have been asked and there are no messages in transit,
     * the algorithm is finished.
     *
     * @return <code>true</code> if finished OR <code>false</code> otherwise
     */
    private boolean askNodesorFinish() throws IOException
    {
        /* If >= CONCURRENCY nodes are in transit, don't do anything */
        if (Configuration.CONCURRENCY <= this.messagesTransiting.size())
        {
            return false;
        }

        /* Get unqueried nodes among the K closest seen */
        ArrayList<Node> unasked = this.closestNodesNotFailed(UNASKED);

        if (unasked.isEmpty() && this.messagesTransiting.isEmpty())
        {
            /* We have no unasked nodes nor any messages in transit, we're finished! */
            error = false;
            return true;
        }

        /* Sort nodes according to criteria */
        Collections.sort(unasked, this.comparator);

        /**
         * Send messages to nodes in the list;
         * making sure than no more than CONCURRENCY messsages are in transit
         */
        for (int i = 0; (this.messagesTransiting.size() < Configuration.CONCURRENCY) && (i < unasked.size()); i++)
        {
            Node n = (Node) unasked.get(i);

            int comm = server.sendMessage(n, lookupMessage, this);

            this.nodes.put(n, AWAITING);
            this.messagesTransiting.put(new Integer(comm), n);
        }

        /* We're not finished as yet, return false */
        return false;
    }

    /**
     * @param status The status of the nodes to return
     *
     * @return The K closest nodes to the target lookupId given that have the specified status
     */
    private ArrayList<Node> closestNodes(Byte status)
    {
        ArrayList<Node> closestNodes = new ArrayList<>(Configuration.K);
        int remainingSpaces = Configuration.K;

        for (Map.Entry e : this.nodes.entrySet())
        {
            if (status.equals(e.getValue()))
            {
                /* We got one with the required status, now add it */
                closestNodes.add((Node) e.getKey());
                if (--remainingSpaces == 0)
                {
                    break;
                }
            }
        }

        return closestNodes;
    }

    /**
     * Find The K closest nodes to the target lookupId given that have not FAILED.
     * From those K, get those that have the specified status
     *
     * @param status The status of the nodes to return
     *
     * @return A List of the closest nodes
     */
    private ArrayList<Node> closestNodesNotFailed(Byte status)
    {
        ArrayList<Node> closestNodes = new ArrayList<>(Configuration.K);
        int remainingSpaces = Configuration.K;

        for (Map.Entry e : this.nodes.entrySet())
        {
            if (!FAILED.equals(e.getValue()))
            {
                if (status.equals(e.getValue()))
                {
                    /* We got one with the required status, now add it */
                    closestNodes.add((Node) e.getKey());
                }

                if (--remainingSpaces == 0)
                {
                    break;
                }
            }
        }

        return closestNodes;
    }

    @Override
    public synchronized void receive(Message incoming, int comm)
    {
        // NodeRepl
    }

    @Override
    public synchronized void timeout(int comm)
    {

    }
}
