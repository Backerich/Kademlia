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
import kademlia.exceptions.UnknownMessageException;
import kademlia.message.Message;
import kademlia.message.NodeLookupMessage;
import kademlia.message.NodeReplyMessage;
import kademlia.node.Node;
import kademlia.node.NodeId;

/**
 * Finds the K closest nodes to a specified identifier
 * The algorithm terminates when it has gotten responses from the K closest nodes it has seen.
 * Nodes that fail to respond are removed from consideration
 *
 * @author Joshua Kissoon
 * @created 20140219
 */
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
    private final Map<Integer, Node> messagesTransiting;

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
     * @throws java.io.IOException
     * @throws kademlia.exceptions.RoutingException
     */
    @Override
    public synchronized void execute() throws IOException, RoutingException
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
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    public List<Node> getClosestNodes()
    {
        return this.closestNodes(ASKED);
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
                System.out.println("Adding node " + o.getNodeId());
                nodes.put(o, UNASKED);
            }
        }

        System.out.println(this.localNode.getNodeId() + " Nodes List: ");
        for (Node o : this.nodes.keySet())
        {
            System.out.println(o.getNodeId() + " hash: " + o.hashCode());
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

        /* Get unqueried nodes among the K closest seen that have not FAILED */
        List<Node> unasked = this.closestNodesNotFailed(UNASKED);
        for (Node nn : unasked)
        {
            System.out.println(nn.getNodeId());
        }

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
    private List<Node> closestNodes(Byte status)
    {
        List<Node> closestNodes = new ArrayList<>(Configuration.K);
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
    private List<Node> closestNodesNotFailed(Byte status)
    {
        List<Node> closestNodes = new ArrayList<>(Configuration.K);
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

    /**
     * Receive and handle the incoming NodeReplyMessage
     *
     * @param comm
     *
     * @throws java.io.IOException
     */
    @Override
    public synchronized void receive(Message incoming, int comm) throws IOException
    {
        /* We receive a NodeReplyMessage with a set of nodes, read this message */
        NodeReplyMessage msg = (NodeReplyMessage) incoming;

        /* Add the origin node to our routing table */
        Node origin = msg.getOrigin();
        System.out.println(this.localNode.getNodeId() + " Lookup Operation Response From: " + origin.getNodeId());
        this.localNode.getRoutingTable().insert(origin);

        /* Set that we've completed ASKing the origin node */
        this.nodes.put(origin, ASKED);

        /* Remove this msg from messagesTransiting since it's completed now */
        this.messagesTransiting.remove(new Integer(comm));

        /* Add the received nodes to our nodes list to query */
        this.addNodes(msg.getNodes());
        this.askNodesorFinish();
    }

    /**
     * A node does not respond or a packet was lost, we set this node as failed
     *
     * @param comm
     *
     * @throws java.io.IOException
     */
    @Override
    public synchronized void timeout(int comm) throws IOException
    {
        /* Get the node associated with this communication */
        Node n = this.messagesTransiting.get(new Integer(comm));

        if (n == null)
        {
            throw new UnknownMessageException("Unknown comm: " + comm);
        }

        /* Mark this node as failed */
        this.nodes.put(n, FAILED);
        this.localNode.getRoutingTable().remove(n);
        this.messagesTransiting.remove(new Integer(comm));

        this.askNodesorFinish();
    }
}
