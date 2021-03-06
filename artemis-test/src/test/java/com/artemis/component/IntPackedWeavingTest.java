package com.artemis.component;

import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.STATIC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;

import com.artemis.ComponentMapper;

public class IntPackedWeavingTest extends PackedWeavingTest {
	
	private TransPackedInt packed;

	@Before @Override
	public void setup() {
		super.setup();
		
		packed = e1.createComponent(TransPackedInt.class);
		packed = e2.createComponent(TransPackedInt.class);
	}

	@Override
	int fieldCount() {
		return 3;
	}

	@Override
	Class<?> fieldType() {
		return int[].class;
	}
	
	@Override
	Class<?> componentType() {
		return TransPackedInt.class;
	}
	
	@Override
	ComponentMapper<?> getMapper() {
		return world.getMapper(TransPackedInt.class);
	}
	
	@Test
	public void packed_component_has_backing_array() throws Exception {
		Field data = field("$data");
		
		assertEquals(PRIVATE | STATIC, data.getModifiers());
		assertEquals(fieldType(), data.getType());
		assertEquals(64 * fieldCount(), ((int[])data.get(null)).length);
		
		Method grow = method("$grow");
		grow.invoke(packed);
		assertEquals(64 * fieldCount() * 2, ((int[])data.get(null)).length);
	}
	
	@Test 
	public void packed_component_replaces_field_access_with_backing_array() throws Exception {
		ComponentMapper<TransPackedInt> mapper = world.getMapper(TransPackedInt.class);
		mapper.get(e1).x(4).y(3).z(5);
		mapper.get(e2).x(2).y(1).z(6);
		
		assertEquals(4, mapper.get(e1).x());
		assertEquals(3, mapper.get(e1).y());
		assertEquals(5, mapper.get(e1).z());
		
		assertEquals(2, mapper.get(e2).x());
		assertEquals(1, mapper.get(e2).y());
		assertEquals(6, mapper.get(e2).z());
		
		try {
			TransPackedInt.class.getDeclaredField("x");
			fail("Failed to remove field from component");
		} catch (Exception e) { /* expected */ }
		try {
			TransPackedInt.class.getDeclaredField("y");
			fail("Failed to remove field from component");
		} catch (Exception e) { /* expected */ }
	}
	
	@Test
	public void packed_component_sub_value() throws Exception {
		ComponentMapper<TransPackedInt> mapper = world.getMapper(TransPackedInt.class);
		mapper.get(e1).x(10).y(-10);
		mapper.get(e2).x(20).y(-20);
		
		mapper.get(e1).subX(30);
		mapper.get(e2).subX(-10);
		
		assertEquals(-20, mapper.get(e1).x());
		assertEquals(30, mapper.get(e2).x());
	}
	
	@Test
	public void packed_component_mul_value() throws Exception {
		ComponentMapper<TransPackedInt> mapper = world.getMapper(TransPackedInt.class);
		mapper.get(e1).x(10).y(20).z(105);
		mapper.get(e2).x(30).y(40).z(10);
		
		mapper.get(e1).mulZ(4);
		mapper.get(e2).mulZ(-10);
		
		assertEquals(420, mapper.get(e1).z());
		assertEquals(-100, mapper.get(e2).z());
	}
	
	@Test
	public void packed_component_div_value() throws Exception {
		ComponentMapper<TransPackedInt> mapper = world.getMapper(TransPackedInt.class);
		mapper.get(e1).x(10).y(20).z(55);
		mapper.get(e2).x(30).y(40).z(10);
		
		mapper.get(e1).divY(4);
		mapper.get(e2).divY(5);
		
		assertEquals(5, mapper.get(e1).y());
		assertEquals(8, mapper.get(e2).y());
	}
}
