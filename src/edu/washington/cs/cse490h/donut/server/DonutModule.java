package edu.washington.cs.cse490h.donut.server;

import java.io.IOException;
import java.net.InetAddress;
import java.util.UUID;

import org.apache.thrift.TProcessor;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransportException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;

import edu.washington.cs.cse490h.donut.business.Node;
import edu.washington.cs.cse490h.donut.service.LocatorClientFactory;
import edu.washington.cs.cse490h.donut.service.NodeLocator;
import edu.washington.cs.cse490h.donut.service.RemoteLocatorClientFactory;
import edu.washington.edu.cs.cse490h.donut.service.KeyId;
import edu.washington.edu.cs.cse490h.donut.service.KeyLocator;
import edu.washington.edu.cs.cse490h.donut.service.TNode;
import edu.washington.edu.cs.cse490h.donut.service.KeyLocator.Iface;

/**
 * @author alevy
 */
public class DonutModule implements Module {

    @Option(name = "--hostname", usage = "the hostname to use for this Node")
    private String hostname      = InetAddress.getLocalHost().getCanonicalHostName();

    @Option(name = "--port", usage = "the port on which to bind this Node's Server (default: 8080)")
    private int    port          = 8080;

    @Option(name = "--key", usage = "the 64-bit key for this Node (default: random)")
    private long   key           = UUID.randomUUID().getMostSignificantBits();

    @Option(name = "--known-host", usage = "the hostname of a known node (default: none)")
    private String knownHostname = null;

    @Option(name = "--known-port", usage = "the port of a known node (default: 8080)")
    private int    knownPort     = 8080;

    public DonutModule() throws Exception {
    }

    public void parseArgs(String[] args) throws IOException {
        CmdLineParser parser = new CmdLineParser(this);
        try {
            parser.parseArgument(args);
            System.out.println("Setup: " + hostname + " " + port + " " + key);
        } catch (CmdLineException e) {
            int start = e.getMessage().indexOf('"') + 1;
            int end = e.getMessage().lastIndexOf('"');
            String wrongArgument = e.getMessage().substring(start, end);
            System.err.println("Unknown argument: " + wrongArgument);
            parser.printUsage(System.err);
            System.err.println();
            System.exit(1);
        }
    }

    public void configure(Binder binder) {
        Node node = new Node(hostname, port, new KeyId(key));
        TNode known = new TNode();
        if (knownHostname != null) {
            known = new TNode(knownHostname, knownPort, null, false);
            node.setSuccessor(known);
        }

        binder.bind(Node.class).toInstance(node);
        binder.bind(TNode.class).toInstance(known);
        binder.bind(LocatorClientFactory.class).to(RemoteLocatorClientFactory.class);
        binder.bind(KeyLocator.Iface.class).to(NodeLocator.class);
        try {
            binder.bind(TServerTransport.class).toInstance(new TServerSocket(port));
        } catch (TTransportException e) {
            System.err.println("Unable to listen on port " + port + ".");
            System.exit(1);
        }
        binder.bind(TProcessor.class).toProvider(TProcessorProvider.class);
        binder.bind(TServer.class).toProvider(TServerProvider.class);
    }

    private class TServerProvider implements Provider<TServer> {
        private final TProcessor       proc;
        private final TServerTransport transport;

        @Inject
        private TServerProvider(TProcessor proc, TServerTransport transport) {
            this.proc = proc;
            this.transport = transport;
        }

        public TServer get() {
            return new TSimpleServer(proc, transport);
        }
    }

    private class TProcessorProvider implements Provider<TProcessor> {
        private final Iface iface;

        @Inject
        private TProcessorProvider(KeyLocator.Iface iface) {
            this.iface = iface;
        }

        public TProcessor get() {
            return new KeyLocator.Processor(iface);
        }
    }
}
