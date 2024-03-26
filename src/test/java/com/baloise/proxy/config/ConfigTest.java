package com.baloise.proxy.config;

import static com.baloise.proxy.config.Config.parseIntArray;
import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class ConfigTest {


	@Test
	public void testParseIntArray() throws Exception {
		assertArrayEquals(new int[] {8888}, parseIntArray("8888"));
		assertArrayEquals(new int[] {8888, 3128}, parseIntArray("8888, 3128"));
		assertArrayEquals(parseIntArray("8888 lksdlf; <>, 3128"), parseIntArray("8888, 3128"));
		assertArrayEquals(new int[] {}, parseIntArray("sdfsdf"));
	}
	
}
