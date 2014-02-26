package kademlia.message;

import java.io.IOException;
import kademlia.core.KadServer;
import kademlia.dht.DHT;
import kademlia.node.Node;
import kademlia.operation.Receiver;

/**
 * Responds to a ContentLookupMessage by sending a ContentMessage containing the requested content;
 * if the requested content is not found, a NodeReplyMessage containing the K closest nodes to the request key is sent.
 *
 * @author Joshua Kissoon
 * @since 20140226
 */
public class ContentLookupReceiver implements Receiver
{

    private final KadServer server;
    private final Node localNode;
    private final DHT dht;

    public ContentLookupReceiver(KadServer server, Node localNode, DHT dht)
    {
        this.server = server;
        this.localNode = localNode;
        this.dht = dht;
    }

    @Override
    public void receive(Message incoming, int comm) throws IOException
    {
        ContentLookupMessage msg = (ContentLookupMessage) incoming;

        /* Check if we can have this data */
        if (this.dht.contains(msg.getParameters()))
        {
            /* Return a ContentMessage with the required data */
            ContentMessage cMsg = new ContentMessage(localNode, this.dht.get(msg.getParameters()));
        }
        else
        {
            /**
             * Return a the K closest nodes to this content identifier
             * We create a NodeLookupReceiver and let this receiver handle this operation
             */
            NodeLookupMessage lkpMsg = new NodeLookupMessage(msg.getOrigin(), msg.getParameters().getKey());
            new NodeLookupReceiver(server, localNode).receive(lkpMsg, comm);
        }
    }

    @Override
    public void timeout(int comm)
    {

    }
}
