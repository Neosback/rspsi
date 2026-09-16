package com.rspsi.ui;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ControlledWorkspaceBridgeTest {
    @Test
    void currentFxmlExposesTheRegionsRequiredByTheControlledBridge() throws Exception {
        Set<String> ids = new HashSet<>();
        try (InputStream stream = getClass().getResourceAsStream("/fxml/main_test4.fxml")) {
            assertNotNull(stream, "Missing current editor FXML");
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
            NodeList nodes = document.getElementsByTagName("*");
            for (int index = 0; index < nodes.getLength(); index++) {
                Element element = (Element) nodes.item(index);
                if (element.hasAttribute("fx:id")) {
                    ids.add(element.getAttribute("fx:id"));
                }
            }
        }
        assertTrue(ids.contains("legacyToolRail"));
        assertTrue(ids.contains("legacyViewport"));
        assertTrue(ids.contains("legacyInspector"));
        assertTrue(ids.contains("grabBar"));
    }
}
