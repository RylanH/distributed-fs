package edu.washington.cs.cse490h.donut.util;

import edu.washington.cs.cse490h.donut.server.DonutClient;
import edu.washington.cs.cse490h.donut.service.LocalLocatorClientFactory;
import edu.washington.edu.cs.cse490h.donut.service.TNode;

public class DonutLeaveClosure extends DonutClosure {

    private final DonutClient               client;
    private final LocalLocatorClientFactory clientFactory;
    private final TNode                     node;

    public DonutLeaveClosure(DonutClient client,
            LocalLocatorClientFactory clientFactory, TNode node) {
        this.client = client;
        this.clientFactory = clientFactory;
        this.node = node;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void run() {
        clientFactory.remove(node);
        client.stop();
    }
}
