package com.artemis;

/**
 * Created by EvilEntity on 14/02/2017.
 */
public class ArtemisUtils {
	public static World world;
	public static <T extends Component> void addComponent(int entityId, T component) {
		ComponentMapper<T> mapper = (ComponentMapper<T>)world.getMapper(component.getClass());
		if (!mapper.has(entityId)) mapper.create(entityId);
		mapper.components.unsafeSet(entityId, component);
	}
}
