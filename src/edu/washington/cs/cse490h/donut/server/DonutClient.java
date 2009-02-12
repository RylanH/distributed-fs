package edu.washington.cs.cse490h.donut.server;

import java.util.logging.Logger;

import org.apache.thrift.TException;

import com.google.inject.Inject;

import edu.washington.cs.cse490h.donut.business.Node;
import edu.washington.cs.cse490h.donut.service.RetryFailedException;
import edu.washington.cs.cse490h.donut.service.LocatorClientFactory;
import edu.washington.cs.cse490h.donut.util.KeyIdUtil;
import edu.washington.edu.cs.cse490h.donut.service.KeyId;
import edu.washington.edu.cs.cse490h.donut.service.TNode;
import edu.washington.edu.cs.cse490h.donut.service.KeyLocator.Iface;

/**
 * 
 * @author alevy
 *
 */
public class DonutClient extends Thread {
    private static final Logger        LOGGER;

    private final Node                 node;
    private final LocatorClientFactory clientFactory;

    private int                        nextFingerToUpdate;

    static {
        LOGGER = Logger.getLogger(DonutClient.class.getName());
    }

    @Inject
    public DonutClient(Node node, LocatorClientFactory clientFactory) {
        this.node = node;
        this.clientFactory = clientFactory;
        nextFingerToUpdate = 0;
    }

    public void join(TNode n) throws TException {
        if (n == null || n.getName() == null) {
            return;
        }
        LOGGER.info("Joining Donut...");
        try {
            if (!n.equals(this.node.getTNode())) {
                TNode found = clientFactory.get(n).findSuccessor(this.node.getNodeId());
                this.node.join(found);
            }
        } catch (RetryFailedException e) {
            throw new TException(e);
        }
        LOGGER.info("Joined Donut!");
    }

    public boolean ping(TNode n) {
        try {
            clientFactory.get(n).ping();
            return true;
        } catch (RetryFailedException e) {
            return false;
        } catch (TException e) {
            // Thrift error. Take a look at the trace
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Called periodically. Checks whether the predecessor has failed.
     */
    public void checkPredecessor() {
        if (this.node.getPredecessor() != null && !ping(this.node.getPredecessor())) {
            // A predecessor is defined but could not be reached. Nullify the current predecessor
            LOGGER.warning("Node " + node.getPredecessor() + " is down!");
            this.node.setPredecessor(null);
        }
    }

    /**
     * Called periodically. Refreshes the finger table entries. nextFingerToUpdate stores the index
     * of the next finger to fix.
     */
    public void fixFingers() {
        if (!node.getSuccessor().equals(node)) {
            return;
        }

        TNode updatedFinger = null;
        try {
            Iface iface;
            try {
                iface = clientFactory.get(node.getSuccessor());
            } catch (RetryFailedException e) {
                return;
            }
            updatedFinger = iface.findSuccessor(new KeyId(this.node.getNodeId().getId()
                    + (long) Math.pow(2, nextFingerToUpdate)));
        } catch (TException e) {
            e.printStackTrace();
            return;
        }

        this.node.setFinger(nextFingerToUpdate, updatedFinger);

        nextFingerToUpdate = (nextFingerToUpdate + 1) % Node.KEYSPACESIZE;
    }

    /**
     * Called periodically. Verify's immediate successor, and tell's successor about us.
     */
    public void stabalize() {

        TNode x = null;
        if (!node.getTNode().equals(node.getSuccessor())) {
            try {
                Iface successorClient = clientFactory.get(node.getSuccessor());
                x = successorClient.getPredecessor();
                successorClient.notify(node.getTNode());
                if (!x.isNil()
                        && KeyIdUtil.isAfterXButBeforeY(x.getNodeId(), node.getNodeId(), node
                                .getSuccessor().getNodeId())) {
                    node.setSuccessor(x);
                }
            } catch (TException e) {
                e.printStackTrace();
                node.setSuccessor(node.getTNode());
                return;
            } catch (RetryFailedException e) {
                e.printStackTrace();
                node.setSuccessor(node.getTNode());
                return;
            }
        } else {
            x = node.getPredecessor();
            if (x != null) {
                node.setSuccessor(x);
            }
        }

    }

    @Override
    public void run() {
        super.run();
        while (true) {
            try {
                stabalize();
                checkPredecessor();
                fixFingers();
                sleep(100);
            } catch (Exception e) {
                //
            }
        }
    }
}