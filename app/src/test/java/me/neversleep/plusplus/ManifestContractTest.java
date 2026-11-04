package me.neversleep.plusplus;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

public class ManifestContractTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test
    public void shellCompatibilityNameIsPermissionProtectedAndSettingsRemainLaunchable() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Element manifest = factory.newDocumentBuilder().parse(new File("src/main/AndroidManifest.xml"))
                .getDocumentElement();
        NodeList activities = manifest.getElementsByTagName("activity");
        Element command = null, settings = null;
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            String name = activity.getAttributeNS(ANDROID, "name");
            if (name.equals("me.neversleep.plusplus.MainActivity")) command = activity;
            if (name.equals("me.neversleep.plusplus.SettingsActivity")) settings = activity;
        }
        assertNotNull(command);
        assertEquals("true", command.getAttributeNS(ANDROID, "exported"));
        assertEquals("android.permission.DUMP", command.getAttributeNS(ANDROID, "permission"));
        assertNotNull(settings);
        assertEquals("true", settings.getAttributeNS(ANDROID, "exported"));
        assertEquals("", settings.getAttributeNS(ANDROID, "permission"));
        assertEquals("", ((Element) manifest.getElementsByTagName("application").item(0))
                .getAttributeNS(ANDROID, "permission"));
        boolean launcher = false, moduleSettings = false;
        NodeList categories = settings.getElementsByTagName("category");
        for (int i = 0; i < categories.getLength(); i++) {
            String name = ((Element) categories.item(i)).getAttributeNS(ANDROID, "name");
            launcher |= name.equals("android.intent.category.LAUNCHER");
            moduleSettings |= name.equals("de.robv.android.xposed.category.MODULE_SETTINGS");
        }
        assertTrue(launcher);
        assertTrue(moduleSettings);
    }
}
