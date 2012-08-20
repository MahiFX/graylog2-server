package org.graylog2.messagehandlers.scribe;

import org.apache.log4j.Logger;
import org.graylog2.messagequeue.MessageQueue;
import org.graylog2.messagehandlers.gelf.InvalidGELFCompressionMethodException;
import org.graylog2.messagehandlers.gelf.SimpleGELFClientHandler;
import org.graylog2.messagehandlers.syslog.GraylogSyslogServerEvent;
import org.graylog2.messagehandlers.syslog.SyslogEventHandler;
import org.productivity.java.syslog4j.server.SyslogServer;
import org.productivity.java.syslog4j.server.SyslogServerIF;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.util.zip.DataFormatException;
import java.util.List;
import java.lang.reflect.Field;

import scribe.thrift.scribe.Iface;
import scribe.thrift.LogEntry;
import scribe.thrift.ResultCode;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.thrift.TException;

/**
 * ScribeHandler.java:
 *
 * Object responsible for handling a Scribe request containing a list of GELF entries
 *
 */
public class ScribeHandler implements Iface {

    private static final Logger LOG = Logger.getLogger(ScribeHandler.class);

    private final MessageQueue queue = MessageQueue.getInstance();
    private int maxSize = -1;

    public ScribeHandler() {
    }
    
    @Override
    public ResultCode Log(List<LogEntry> messages) throws TException {
        LOG.info("Received " + messages.size() + " messages.");

        synchronized (queue) {
            if (maxSize == -1) {
                // HACK
                try {
                    Field field = MessageQueue.class.getDeclaredField("sizeLimit");
                    field.setAccessible(true);
                    maxSize = ((Integer)field.get(queue)).intValue();
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }

                LOG.trace("MessageQueue maxSize: " + maxSize);

            } else if (maxSize > 0) {
                if (queue.getSize() + messages.size() >= maxSize) {
                    LOG.warn("MessageQueue over capacity, returning TRY_LATER: queueSize=" + queue.getSize() + ", newMessageSize=" + messages.size() + ", maxSize=" + maxSize);
                    return ResultCode.TRY_LATER;
                }
            }

            for (LogEntry message : messages) {
                LOG.trace("received new scribe message: category= " + message.category + " message= " + message.message);
                try {
                    handleMessage(message.message);
                } catch (Exception ex) {
                    LOG.error("Failed to process message: category= " + message.category + " message= " + message.message);
                    ex.printStackTrace();
                    throw new RuntimeException("Exception processing event", ex);
                }
            }
        }
        
        return ResultCode.OK;
    }
    
    private void handleMessage(String msgBody) throws DataFormatException, UnsupportedEncodingException, InvalidGELFCompressionMethodException, IOException {
        // Handle GELF message.
        SimpleGELFClientHandler gelfHandler = new SimpleGELFClientHandler(new String(msgBody));
        gelfHandler.handle();
    }
}
