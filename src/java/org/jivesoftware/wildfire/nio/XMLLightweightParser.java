/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.nio;

import org.apache.mina.common.ByteBuffer;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * This is a Light-Weight XML Parser.
 * It read data from a channel and collect data until data are available in
 * the channel.
 * When a message is complete you can retrieve messages invoking the method
 * getMsgs() and you can invoke the method areThereMsgs() to know if at least
 * an message is presents.
 *
 * @author Daniele Piras
 * @author Gaston Dombiak
 */
class XMLLightweightParser {
    // Chars that rappresent CDATA section start
    protected static char[] CDATA_START = {'<', '!', '[', 'C', 'D', 'A', 'T', 'A', '['};
    // Chars that rappresent CDATA section end
    protected static char[] CDATA_END = {']', ']', '>'};

    // Buffer with all data retrieved
    protected StringBuilder buffer = new StringBuilder();

    // ---- INTERNAL STATUS -------
    // Initial status
    protected static final int INIT = 0;
    // Status used when the first tag name is retrieved
    protected static final int HEAD = 2;
    // Status used when robot is inside the xml and it looking for the tag conclusion
    protected static final int INSIDE = 3;
    // Status used when a '<' is found and try to find the conclusion tag.
    protected static final int PRETAIL = 4;
    // Status used when the ending tag is equal to the head tag
    protected static final int TAIL = 5;
    // Status used when robot is inside the main tag and found an '/' to check '/>'.
    protected static final int VERIFY_CLOSE_TAG = 6;
    //  Status used when you are inside a parameter
    protected static final int INSIDE_PARAM_VALUE = 7;
    //  Status used when you are inside a cdata section
    protected static final int INSIDE_CDATA = 8;


    // Current robot status
    protected int status = XMLLightweightParser.INIT;

    // Index to looking for a CDATA section start or end.
    protected int cdataOffset = 0;

    // Number of chars that machs with the head tag. If the tailCount is equal to
    // the head length so a close tag is found.
    protected int tailCount = 0;
    // Indicate the starting point in the buffer for the next message.
    protected int startLastMsg = 0;
    // Flag used to discover tag in the form <tag />.
    protected boolean insideRootTag = false;
    // Object conteining the head tag
    protected StringBuilder head = new StringBuilder(5);
    // List with all finished messages found.
    protected List<String> msgs = new ArrayList<String>();

    protected boolean insideChildrenTag = false;

    ByteBuffer byteBuffer;
    Charset encoder;

    public XMLLightweightParser(String charset) {
        encoder = Charset.forName(charset);
    }

    /*
    * true if the parser has found some complete xml message.
    */
    public boolean areThereMsgs() {
        return (msgs.size() > 0);
    }

    /*
    * @return an array with all messages found
    */
    public String[] getMsgs() {
        String[] res = new String[msgs.size()];
        for (int i = 0; i < res.length; i++) {
            res[i] = msgs.get(i);
        }
        msgs.clear();
        invalidateBuffer();
        return res;
    }

    /*
    * Method use to re-initialize the buffer
    */
    protected void invalidateBuffer() {
        if (buffer.length() > 0) {
            String str = buffer.substring(startLastMsg);
            buffer.delete(0, buffer.length());
            buffer.append(str);
            buffer.trimToSize();
        }
        startLastMsg = 0;
    }


    /*
    * Method that add a message to the list and reinit parser.
    */
    protected void foundMsg(String msg) {
        // Add message to the complete message list
        if (msg != null) {
            msgs.add(msg);
        }
        // Move the position into the buffer
        status = XMLLightweightParser.INIT;
        tailCount = 0;
        cdataOffset = 0;
        head.setLength(0);
        insideRootTag = false;
        insideChildrenTag = false;
    }

    /*
    * Main reading method
    */
    public void read(ByteBuffer byteBuffer) throws Exception {
        int readByte = byteBuffer.remaining();

        invalidateBuffer();
        CharBuffer charBuffer = encoder.decode(byteBuffer.buf());
        //charBuffer.flip();
        char[] buf = charBuffer.array();

        buffer.append(buf);
        // Robot.
        char ch;
        for (int i = 0; i < readByte; i++) {
            //ch = rawByteBuffer[ i ];
            ch = buf[i];
            if (status == XMLLightweightParser.TAIL) {
                // Looking for the close tag
                if (ch == head.charAt(tailCount)) {
                    tailCount++;
                    if (tailCount == head.length()) {
                        // Close tag found!
                        // Calculate the correct start,end position of the message into the buffer
                        int end = buffer.length() - readByte + (i + 1);
                        String msg = buffer.substring(startLastMsg, end);
                        // Add message to the list
                        foundMsg(msg);
                        startLastMsg = end;
                    }
                } else {
                    tailCount = 0;
                    status = XMLLightweightParser.INSIDE;
                }
            } else if (status == XMLLightweightParser.PRETAIL) {
                if (ch == XMLLightweightParser.CDATA_START[cdataOffset]) {
                    cdataOffset++;
                    if (cdataOffset == XMLLightweightParser.CDATA_START.length) {
                        status = XMLLightweightParser.INSIDE_CDATA;
                        cdataOffset = 0;
                        continue;
                    }
                } else {
                    cdataOffset = 0;
                    status = XMLLightweightParser.INSIDE;
                }
                if (ch == '/') {
                    status = XMLLightweightParser.TAIL;
                }
            } else if (status == XMLLightweightParser.VERIFY_CLOSE_TAG) {
                if (ch == '>') {
                    // Found a tag in the form <tag />
                    int end = buffer.length() - readByte + (i + 1);
                    String msg = buffer.substring(startLastMsg, end);
                    // Add message to the list
                    foundMsg(msg);
                    startLastMsg = end;
                } else {
                    status = XMLLightweightParser.INSIDE;
                }
            } else if (status == XMLLightweightParser.INSIDE_PARAM_VALUE) {

                if (ch == '"') {
                    status = XMLLightweightParser.INSIDE;
                    continue;
                }
            } else if (status == XMLLightweightParser.INSIDE_CDATA) {
                if (ch == XMLLightweightParser.CDATA_END[cdataOffset]) {
                    cdataOffset++;
                    if (cdataOffset == XMLLightweightParser.CDATA_END.length) {
                        status = XMLLightweightParser.INSIDE;
                        cdataOffset = 0;
                        continue;
                    }
                } else {
                    cdataOffset = 0;
                }
            } else if (status == XMLLightweightParser.INSIDE) {
                if (ch == XMLLightweightParser.CDATA_START[cdataOffset]) {
                    cdataOffset++;
                    if (cdataOffset == XMLLightweightParser.CDATA_START.length) {
                        status = XMLLightweightParser.INSIDE_CDATA;
                        cdataOffset = 0;
                        continue;
                    }
                } else {
                    cdataOffset = 0;
                }
                if (ch == '"') {
                    status = XMLLightweightParser.INSIDE_PARAM_VALUE;
                } else if (ch == '>') {
                    if (insideRootTag &&
                            ("stream:stream>".equals(head.toString()) || ("?xml>".equals(head.toString())))) {
                        // Found closing stream:stream
                        int end = buffer.length() - readByte + (i + 1);
                        // Skip LF, CR and other "weird" characters that could appear
                        while (startLastMsg < end && '<' != buffer.charAt(startLastMsg)) {
                            startLastMsg++;
                        }
                        String msg = buffer.substring(startLastMsg, end);
                        foundMsg(msg);
                        startLastMsg = end;
                    }
                    insideRootTag = false;
                } else if (ch == '<') {
                    status = XMLLightweightParser.PRETAIL;
                    insideChildrenTag = true;
                } else if (ch == '/' && insideRootTag && !insideChildrenTag) {
                    status = XMLLightweightParser.VERIFY_CLOSE_TAG;
                }
            } else if (status == XMLLightweightParser.HEAD) {
                if (ch == ' ' || ch == '>') {
                    // Append > to head to facility the research of </tag>
                    head.append(">");
                    status = XMLLightweightParser.INSIDE;
                    insideRootTag = true;
                    insideChildrenTag = false;
                    continue;
                }
                head.append(ch);

            } else if (status == XMLLightweightParser.INIT) {
                if (ch != ' ' && ch != '\r' && ch != '\n' && ch != '<') {
                    invalidateBuffer();
                    return;
                }
                if (ch == '<') {
                    status = XMLLightweightParser.HEAD;
                }
            }
        }
        if (head.length() > 0 && "/stream:stream>".equals(head.toString())) {
            // Found closing stream:stream
            foundMsg("</stream:stream>");
        }
    }
}
