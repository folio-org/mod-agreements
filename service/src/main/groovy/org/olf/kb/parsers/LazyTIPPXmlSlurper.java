package org.olf.kb.parsers;

import groovy.namespace.QName;
import groovy.xml.FactorySupport;
import groovy.xml.XmlSlurper;
import groovy.xml.slurpersupport.GPathResult;
import groovy.xml.slurpersupport.NamespaceAwareHashMap;
import groovy.xml.slurpersupport.Node;
import groovy.xml.slurpersupport.NodeChild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

// This is largely copied from XmlSlurper, with extra handling on TIPP nodes
public class LazyTIPPXmlSlurper extends XmlSlurper {
    private static final Logger log = LoggerFactory.getLogger(LazyTIPPXmlSlurper.class);
    private final XMLReader reader;
    private Node currentNode = null;
    private final Stack<Node> stack = new Stack<>();
    private final StringBuilder charBuffer = new StringBuilder();
    private final Map<String, String> namespaceTagHints = new HashMap<>();
    private boolean keepIgnorableWhitespace = false;
    private boolean namespaceAware = false;

    // Special TIPP parsing
    private boolean inTIPP = false;
    private StringBuilder tippBuffer = new StringBuilder();

    // Replicate XmlSlurper behaviour
    public LazyTIPPXmlSlurper() throws ParserConfigurationException, SAXException {
        this(false, true);
    }

    public LazyTIPPXmlSlurper(final boolean validating, final boolean namespaceAware) throws ParserConfigurationException, SAXException {
        this(validating, namespaceAware, false);
    }

    public LazyTIPPXmlSlurper(final boolean validating, final boolean namespaceAware, boolean allowDocTypeDeclaration) throws ParserConfigurationException, SAXException {
        SAXParserFactory factory = FactorySupport.createSaxParserFactory();
        factory.setNamespaceAware(namespaceAware);
        this.namespaceAware = namespaceAware;
        factory.setValidating(validating);
        setQuietly(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setQuietly(factory, "http://apache.org/xml/features/disallow-doctype-decl", !allowDocTypeDeclaration);
        reader = factory.newSAXParser().getXMLReader();
    }

    public LazyTIPPXmlSlurper(final XMLReader reader) throws ParserConfigurationException, SAXException {
        this.reader = reader;
    }

    public LazyTIPPXmlSlurper(final SAXParser parser) throws ParserConfigurationException, SAXException {
        this(parser.getXMLReader());
    }

    private static void setQuietly(SAXParserFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        }
        catch (ParserConfigurationException | SAXNotSupportedException | SAXNotRecognizedException ignored) { }
    }

    public void setKeepIgnorableWhitespace(boolean keepIgnorableWhitespace) {
        this.keepIgnorableWhitespace = keepIgnorableWhitespace;
    }

    public boolean isKeepIgnorableWhitespace() {
        return keepIgnorableWhitespace;
    }


    public GPathResult getDocument() {
        try {
            // xml namespace is always defined
            if (namespaceAware) {
                namespaceTagHints.put("xml", "http://www.w3.org/XML/1998/namespace");
            }
            return new NodeChild(currentNode, null, namespaceTagHints);
        } finally {
            currentNode = null;
        }
    }

    public GPathResult parse(final InputSource input) throws IOException, SAXException {
        reader.setContentHandler(this);
        reader.parse(input);
        return getDocument();
    }

    public GPathResult parse(final File file) throws IOException, SAXException {
        final FileInputStream fis = new FileInputStream(file);
        final InputSource input = new InputSource(fis);
        input.setSystemId("file://" + file.getAbsolutePath());
        try {
            return parse(input);
        } finally {
            fis.close();
        }
    }

    public GPathResult parse(final InputStream input) throws IOException, SAXException {
        return parse(new InputSource(input));
    }

    public GPathResult parse(final Reader in) throws IOException, SAXException {
        return parse(new InputSource(in));
    }

    public GPathResult parse(final String uri) throws IOException, SAXException {
        return parse(new InputSource(uri));
    }

    public GPathResult parse(final Path path) throws IOException, SAXException {
        return parse(Files.newInputStream(path));
    }

    @Override
    public void startDocument() throws SAXException {
        currentNode = null;
        charBuffer.setLength(0);
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        if (namespaceAware) namespaceTagHints.put(prefix, uri);
    }

    @Override
    public void startElement(final String namespaceURI, final String localName, final String qName, final Attributes atts) throws SAXException {
        // Flush any accumulated characters (they belong to the previous element)
        addCdata();

        // If we're already inside a TIPP element, simply accumulate the raw XML.
        if (inTIPP) {
            // Write the start tag with attributes
            tippBuffer.append("<").append(qName);
            for (int i = 0; i < atts.getLength(); i++) {
                String attrName;
                if (atts.getURI(i).isEmpty()) {
                    attrName = atts.getQName(i);
                } else {
                    attrName = new QName(atts.getURI(i), atts.getLocalName(i)).toString();
                }
                tippBuffer.append(" ").append(attrName)
                        .append("=\"").append(atts.getValue(i)).append("\"");
            }
            tippBuffer.append(">");
            return;
        }

        // Check if this element is a TIPP element.
        if ("TIPP".equals(localName)) {
            // Switch to raw mode for TIPP: do not create a Node; instead, accumulate raw XML.
            inTIPP = true;
            tippBuffer.setLength(0); // clear previous content
            tippBuffer.append("<").append(qName);
            for (int i = 0; i < atts.getLength(); i++) {
                String attrName;
                if (atts.getURI(i).isEmpty()) {
                    attrName = atts.getQName(i);
                } else {
                    attrName = new QName(atts.getURI(i), atts.getLocalName(i)).toString();
                }
                tippBuffer.append(" ").append(attrName)
                        .append("=\"").append(atts.getValue(i)).append("\"");
            }
            tippBuffer.append(">");
            // Do not modify currentNode or push to stack – we are handling TIPP specially.
            return;
        }

        // For normal (non-TIPP) elements, proceed as before.
        final Node newElement;
        final Map<String, String> attributes = new NamespaceAwareHashMap();
        final Map<String, String> attributeNamespaces = new HashMap<>();
        for (int i = 0; i < atts.getLength(); i++) {
            if (atts.getURI(i).isEmpty()) {
                attributes.put(atts.getQName(i), atts.getValue(i));
            } else {
                String key = new QName(atts.getURI(i), atts.getLocalName(i)).toString();
                attributes.put(key, atts.getValue(i));
                attributeNamespaces.put(key, atts.getURI(i));
            }
        }
        // If no namespace, use qName; otherwise use localName.
        if (namespaceURI.isEmpty()) {
            newElement = new Node(currentNode, qName, attributes, attributeNamespaces, namespaceURI);
        } else {
            newElement = new Node(currentNode, localName, attributes, attributeNamespaces, namespaceURI);
        }
        if (currentNode != null) {
            currentNode.addChild(newElement);
        }
        stack.push(currentNode);
        currentNode = newElement;
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (inTIPP) {
            // Accumulate raw text within TIPP element.
            tippBuffer.append(ch, start, length);
        } else {
            charBuffer.append(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] buffer, int start, int len) throws SAXException {
        if (keepIgnorableWhitespace) characters(buffer, start, len);
    }

    @Override
    public void endElement(final String namespaceURI, final String localName, final String qName) throws SAXException {
        if (inTIPP) {
            // Append the closing tag to the raw XML
            tippBuffer.append("</").append(qName).append(">");
            // When the TIPP element ends, check if it is the outer TIPP element.
            if ("TIPP".equals(localName)) {
                inTIPP = false;
                // Instead of creating a Node for TIPP, add the raw XML string as a child of the current node.
                if (currentNode != null) {
                    log.info("LOGDEBUG TIPPBUFFER" + tippBuffer.toString());
                    currentNode.addChild(
                        tippBuffer.toString()
                    );
                } else {
                    // If there is no currentNode (shouldn't happen if TIPP is inside a container like TIPPs),
                    // you may decide how to handle this case.
                    log.warn("TIPP element encountered with no parent node.");
                }
                tippBuffer.setLength(0); // clear the buffer for future TIPPs
            }
            return;
        } else {
            // For normal (non-TIPP) elements, first flush any character data.
            addCdata();
            // Pop the stack to set the parent as current.
            currentNode = stack.pop();
        }
    }

    @Override
    public void endDocument() throws SAXException {
        // Document finished. Additional cleanup if needed.
    }

    private void addCdata() {
        if (!charBuffer.isEmpty()) {
            final String cdata = charBuffer.toString();
            charBuffer.setLength(0);
            // Only add if not just ignorable whitespace (unless configured to keep it)
            if (keepIgnorableWhitespace || !cdata.trim().isEmpty()) {
                if (currentNode != null) {
                    currentNode.addChild(cdata);
                }
            }
        }
    }
}