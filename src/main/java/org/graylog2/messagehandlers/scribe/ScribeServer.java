package org.graylog2.messagehandlers.scribe;

import org.apache.log4j.Logger;
import scribe.thrift.scribe;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.THsHaServer;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TTransportException;
import org.apache.thrift.transport.TTransportFactory;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * ScribeServer.java:
 *
 * Implements the Thrift threadpool necessary to receive Scribe messages
 *
 */
public final class ScribeServer implements Runnable {

    private static final Logger LOG = Logger.getLogger(ScribeServer.class);
    

    TNonblockingServerSocket server;
    private String host = "localhost";
    private int port = 5672;
    private ScribeHandler handler;
    private int rpc_timeout;
    private int thrift_length;
    private int min_threads;
    private int max_threads;

    public ScribeServer(String set_host, int set_port, int set_rpc_timeout, int set_thrift_length, int set_min_threads, int set_max_threads) {
        setHost(set_host);
        setPort(set_port);
        this.rpc_timeout = set_rpc_timeout;
        this.thrift_length = set_thrift_length;
        this.min_threads = set_min_threads;
        this.max_threads = set_max_threads;
        this.handler = new ScribeHandler();
    }
    
    @Override
    public void run() {
        LOG.info("Starting Scribe server on port :" + String.valueOf( this.port) );
        
        scribe.Processor processor = new scribe.Processor(handler);
        
        try {
            server = new TNonblockingServerSocket(new InetSocketAddress(this.host, this.port),
                                       rpc_timeout);
        } catch (TTransportException e) {
            throw new RuntimeException(String.format("Unable to create scribe server socket to %s:%s",
                                                     this.host, this.port), e);
        }
        
        // Protocol factory
        TProtocolFactory tProtocolFactory = new TBinaryProtocol.Factory(false,
                                                                        false,
                                                                        thrift_length);
        
        // Transport factory
        TTransportFactory inTransportFactory, outTransportFactory;
        int tFramedTransportSize = thrift_length;
        inTransportFactory  = new TFramedTransport.Factory(tFramedTransportSize);
        outTransportFactory = new TFramedTransport.Factory(tFramedTransportSize);
        LOG.info("Using TFastFramedTransport with a max frame size of " + String.valueOf( this.thrift_length) + " bytes");
        
        // ThreadPool Server
        THsHaServer.Args args = new THsHaServer.Args(server)
        .inputTransportFactory(inTransportFactory)
        .outputTransportFactory(outTransportFactory)
        .inputProtocolFactory(tProtocolFactory)
        .outputProtocolFactory(tProtocolFactory)
        .processor(processor);
        
        THsHaServer ttps = new THsHaServer(args);
        ttps.serve();
    }
    
    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @param host the host to set
     */
    private void setHost(String host) {
        if (host != null && host.length() > 0) {
            this.host = host;
        }
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    private void setPort(int port) {
        if (port > 0) {
            this.port = port;
        }
    }


}