package com.salesmanager.test.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import com.salesmanager.test.common.AbstractSalesManagerCoreTestCase;
import com.salesmanager.test.configuration.ConfigurationTest;

public class JupiterPlatformTest {
    @Test
    void onlyJupiterIsAvailable() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        for (String name : new String[] {"org.junit.Test", "org.junit.runner.RunWith",
                "junit.framework.TestCase", "org.junit.vintage.engine.VintageTestEngine"}) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(name, false, loader), name);
        }
        assertNotNull(Class.forName("org.junit.jupiter.engine.JupiterTestEngine", false, loader));
    }

    @Test
    void coreSupportRemainsAbstractAndBootConfigured() {
        assertTrue(Modifier.isAbstract(AbstractSalesManagerCoreTestCase.class.getModifiers()));
        SpringBootTest annotation = AbstractSalesManagerCoreTestCase.class.getAnnotation(SpringBootTest.class);
        assertNotNull(annotation);
        assertArrayEquals(new Class<?>[] {ConfigurationTest.class}, annotation.classes());
    }

    @Test
    void jsonSimpleParsesAndRoundTripsWithoutJunit() throws Exception {
        JSONParser parser = new JSONParser();
        JSONObject object = (JSONObject) parser.parse("{\"name\":\"shop\",\"items\":[1,true,null]}");
        assertEquals("shop", object.get("name"));
        JSONArray items = (JSONArray) object.get("items");
        assertEquals(3, items.size());
        assertEquals(1L, items.get(0));
        assertEquals(Boolean.TRUE, items.get(1));
        assertNull(items.get(2));
        assertEquals(object, parser.parse(object.toJSONString()));
        assertTrue(((JSONArray) parser.parse("[]")).isEmpty());
        assertThrows(org.json.simple.parser.ParseException.class, () -> parser.parse("{broken"));
    }
}
