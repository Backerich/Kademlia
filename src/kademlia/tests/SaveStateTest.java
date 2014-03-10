package kademlia.tests;

import kademlia.core.Kademlia;
import kademlia.node.NodeId;

/**
 * Testing the save and retrieve state operations
 *
 * @author Joshua Kissoon
 * @since 20140309
 */
public class SaveStateTest
{

    public SaveStateTest()
    {
        try
        {
            /* Setting up 2 Kad networks */
            Kademlia kad1 = new Kademlia("JoshuaK", new NodeId("ASF45678947584567467"), 7529);
            Kademlia kad2 = new Kademlia("Crystal", new NodeId("ASERTKJDHGVHERJHGFLK"), 7532);

            /* Connecting 2 to 1 */
            System.out.println("Connecting Kad 1 and Kad 2");
            kad1.connect(kad2.getNode());

            synchronized (this)
            {
                DHTContentImpl c = new DHTContentImpl(kad2.getOwnerId(), "Some Data");
                System.out.println(c);
                kad2.put(c);
            }

            System.out.println(kad1);
            System.out.println(kad2);

            /* Shutting down kad1 and restarting it */
            System.out.println("\n\n\nShutting down Kad instance");
            kad1.shutdown();

            System.out.println("\n\n\nReloading down Kad instance from file");
            Kademlia kad3 = Kademlia.loadFromFile("JoshuaK");
            System.out.println(kad3);
        }
        catch (Exception e)
        {
            e.printStackTrace();;
        }
    }

    public static void main(String[] args)
    {
        new SaveStateTest();
    }
}
