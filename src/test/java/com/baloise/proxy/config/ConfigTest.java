package com.baloise.proxy.config;

import static com.baloise.proxy.config.Config.parseIntArray;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.baloise.proxy.config.Config.UIType;

public class ConfigTest {


	@Test
	public void parseUITypeNull() throws Exception {
		assertEquals(UIType.SWT, UIType.parse(null));
	}
	@Test
	public void parseUITypeEmpty() throws Exception {
		assertEquals(UIType.SWT, UIType.parse(""));
	}
	
	@Test
	public void parseUITypeGibberish() throws Exception {
		assertEquals(UIType.SWT, UIType.parse("lkjah ads654a54sdf"));
	}
	
	@Test
	public void parseUITypawt() throws Exception {
		assertEquals(UIType.AWT, UIType.parse(" awt "));
	}
	
	@Test
	public void parseUITypAWT() throws Exception {
		assertEquals(UIType.AWT, UIType.parse(" AWT "));
	}
	
	@Test
	public void parseUITypSWT() throws Exception {
		assertEquals(UIType.SWT, UIType.parse("SWT"));
	}
	
	@Test
	public void testParseIntArray() throws Exception {
		assertArrayEquals(new int[] {8888}, parseIntArray("8888"));
		assertArrayEquals(new int[] {8888, 3128}, parseIntArray("8888, 3128"));
		assertArrayEquals(parseIntArray("8888 lksdlf; <>, 3128"), parseIntArray("8888, 3128"));
		assertArrayEquals(new int[] {}, parseIntArray("sdfsdf"));
	}
	
	
}
