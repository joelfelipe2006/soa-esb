package br.senai.fatesg.soa.common;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Utilitários de XML (só JDK). Usado pelo ESB e pelo sistema legado. */
public final class Xml {

    public static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";

    private Xml() {
    }

    /** Lê um XML com segurança (sem DTD, sem entidades externas). */
    public static Document parse(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(null);
            return b.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new IllegalArgumentException("XML inválido: " + e.getMessage());
        }
    }

    /** Filhos que são elementos. */
    public static List<Element> children(Element parent) {
        List<Element> list = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                list.add((Element) nodes.item(i));
            }
        }
        return list;
    }

    /** Pega o primeiro elemento dentro do soap:Body (a operação). */
    public static Element soapOperation(Document doc) {
        NodeList bodies = doc.getElementsByTagNameNS(SOAP_NS, "Body");
        if (bodies.getLength() == 0) {
            throw new IllegalArgumentException("XML sem soap:Body");
        }
        List<Element> ops = children((Element) bodies.item(0));
        if (ops.isEmpty()) {
            throw new IllegalArgumentException("soap:Body vazio");
        }
        return ops.get(0);
    }

    /** Campos simples de um elemento: nome local -> texto. */
    public static Map<String, String> fields(Element parent) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Element e : children(parent)) {
            m.put(e.getLocalName(), e.getTextContent().trim());
        }
        return m;
    }

    /** Monta um envelope SOAP com uma operação e seus campos. */
    public static String soapEnvelope(String prefix, String namespace, String operation, Map<String, ?> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<soap:Envelope xmlns:soap=\"").append(SOAP_NS).append("\" xmlns:")
                .append(prefix).append("=\"").append(namespace).append("\">\n");
        sb.append("  <soap:Body>\n");
        sb.append("    <").append(prefix).append(':').append(operation).append(">\n");
        for (Map.Entry<String, ?> e : fields.entrySet()) {
            sb.append("      <").append(prefix).append(':').append(e.getKey()).append('>')
                    .append(escape(String.valueOf(e.getValue())))
                    .append("</").append(prefix).append(':').append(e.getKey()).append(">\n");
        }
        sb.append("    </").append(prefix).append(':').append(operation).append(">\n");
        sb.append("  </soap:Body>\n");
        sb.append("</soap:Envelope>");
        return sb.toString();
    }

    public static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
