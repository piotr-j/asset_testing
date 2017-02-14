package io.piotrjastrzebski;

import com.artemis.*;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;

import java.util.Comparator;

/**
 * We want to figure out this renderable bullshit
 *
 * We have _base_ these types:
 * 	TextureRegion
 * 	Spine
 * Then, we can modify how they change during game play
 * 	Animation - fixed amount of frames, looped changes over time
 * 	Facing - renderable is changed based on entities facing direction
 * 	Tiled - renderable is changed based on adjacent entities of same type
 * 	Shader? - Applied to this asset
 *
 * Facing and Tiled are exclusive
 * Animation and Spine combination doesnt make sense
 *
 * we want to be able to specify combinations, stuff has order, ie facing/tiled before animation
 *  tint?
 *  facing 		-> animation 	-> region
 *  facing 							-> region
 *  tiled 		-> animation 	-> region
 *  tiled 							-> region
 *  animation 						-> region
 *  										region
 *
 *  facing -> spine
 *  tiled -> spine
 *  spine
 *
 *  renderable
 *  	preprocessors?
 * 	asset (region/spine)
 *    shader?
 *
 *  facing - pick stuff based on some state
 *  	animation - pick stuff based on some state
 *  		region
 *  		region
 *  	animation
 *  		region
 *  		region
 *
 *  facing
 *  	region
 *  	region
 */
public class MyGdxGame extends ApplicationAdapter {

	public static class Facing extends Component {
		Animation af1;
		Animation af2;
		Region rf1;
		Region rf2;
	}

	public static class Animation extends Component {
		public com.badlogic.gdx.graphics.g2d.Animation<TextureRegion> animation;
	}

	public static class Region extends Component {
		public TextureRegion region;
	}

	public static class Spine extends Component {
		// TODO
	}



	SpriteBatch batch;
	World world;
	protected ComponentMapper<Renderable> mRenderable;
	protected ComponentMapper<Animation> mAnimation;
	@Override
	public void create () {
		batch = new SpriteBatch();

		WorldConfiguration config = new WorldConfiguration();
		config.register(batch);

		Renderer renderer = new Renderer();
		config.setSystem(renderer);

		world = new World(config);
		world.inject(this, false);

		renderer.addSubRenderer(new RegionRenderer());
		Renderer.PreProcessor animationPreprocessor;
		renderer.addPreprocessor(animationPreprocessor = new Renderer.PreProcessor() {
			protected ComponentMapper<Animation> mAnimation;
			protected ComponentMapper<Region> mRegion;
			@Override public boolean accept (int entityId) {
				return mAnimation.has(entityId) && mRegion.has(entityId);
			}

			@Override public void process (int entityId) {
				Animation animation = mAnimation.get(entityId);
				Region region = mRegion.get(entityId);
				//region.region = animation.animation.getKeyFrame(0);
				Gdx.app.log("Animations", "Process " + entityId);
			}

			@Override public int priority () {
				return 0;
			}
		});


		{
			int entityId = world.create();
			Renderable renderable = mRenderable.create(entityId);
			renderable.type = Renderable.TYPE_REGION;
		}
		{
			int entityId = world.create();
			Renderable renderable = mRenderable.create(entityId);
			renderable.type = Renderable.TYPE_REGION;
			mAnimation.create(entityId);

			renderer.register(entityId, animationPreprocessor);
		}
	}

	@Override
	public void render () {
		Gdx.gl.glClearColor(1, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		world.process();
	}

	public static class Renderable extends Component {
		public final static int TYPE_REGION = 0;
		public final static int TYPE_SPINE = 1;
		public float x;
		public float y;
		public int type;
		// TODO Spine
		public transient Array<Renderer.PreProcessor> preProcessors = new Array<Renderer.PreProcessor>();
	}

	public static class Renderer extends BaseEntitySystem {
		private static final String TAG = Renderer.class.getSimpleName();
		protected ComponentMapper<Renderable> mRenderable;
		protected int nextId;
		protected SubRenderer[] renderers;
		protected Array<PreProcessor> preProcessors;
		public Renderer () {
			super(Aspect.all(Renderable.class));
			renderers = new SubRenderer[16];
			preProcessors = new Array<PreProcessor>();
		}

		@Override protected void initialize () {
		}

		@Override protected void processSystem () {
			IntBag entityIds = getEntityIds();
			int[] rawIds = entityIds.getData();
			// TODO sort by layer, z, whatever
			for (int i = 0; i < entityIds.size(); i++) {
				int entityId = rawIds[i];
				Renderable renderable = mRenderable.get(entityId);
				for (PreProcessor preProcessor : renderable.preProcessors) {
					preProcessor.process(entityId);
				}
				SubRenderer renderer = renderers[renderable.type];
				if (renderer != null) {
					renderer.begin();
					renderer.render(entityId);
					renderer.end();
				}
			}
		}

		public void addSubRenderer(SubRenderer subRenderer) {
			subRenderer.id(nextId);
			renderers[nextId++] = subRenderer;
			world.inject(subRenderer, false);
		}

		public void addPreprocessor(PreProcessor preProcessor) {
			preProcessors.add(preProcessor);
			preProcessors.sort();
			world.inject(preProcessor, false);
		}

		public void register(int entityId, PreProcessor preProcessor) {
			Renderable renderable = mRenderable.get(entityId);
			if (!renderable.preProcessors.contains(preProcessor, true)) {
				renderable.preProcessors.add(preProcessor);
			}
		}

		public void unregister(int entityId, PreProcessor preProcessor) {
			Renderable renderable = mRenderable.get(entityId);
			renderable.preProcessors.removeValue(preProcessor, true);
		}

		public static abstract class SubRenderer {
			private int id;

			public abstract void begin();
			public abstract void render(int entityId);
			public abstract void end();

			public void id (int id) {
				this.id = id;
			}

			public int id () {
				return id;
			}
		}

		public static abstract class PreProcessor implements Comparable<PreProcessor> {
			public abstract boolean accept(int entityId);
			public abstract void process(int entityId);
			public abstract int priority();

			@Override public int compareTo (PreProcessor o) {
				return priority()-o.priority();
			}
		}
	}

	public static class RegionRenderer extends Renderer.SubRenderer {
		private static final String TAG = RegionRenderer.class.getSimpleName();
		protected ComponentMapper<Renderable> mRenderable;
		protected ComponentMapper<Region> mRegion;
		@Override public void begin () {

		}

		@Override public void render (int entityId) {
			Renderable renderable = mRenderable.get(entityId);
			Region region = mRegion.get(entityId);
			Gdx.app.log(TAG, "Render region " + entityId);
		}

		@Override public void end () {

		}
	}
	
	@Override
	public void dispose () {
		world.dispose();
		batch.dispose();
	}
}
