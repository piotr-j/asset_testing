package io.piotrjastrzebski;

import com.artemis.*;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;

/**
 * We want to figure out this renderable bullshit
 *
 * We have _base_ these types:
 * 	TextureRegion
 * 	Spine
 * Then, we can modify how they change during game play
 * 	Animation - fixed amount of frames, looped changes over stateTime
 * 	Facing - renderable is changed based on entities facing direction
 * 	Tiled - renderable is changed based on adjacent entities of same type
 * 	Shader? - Applied to this asset
 *
 * Facing and Tiled are exclusive
 * Animation and Spine combination doesnt make sense
 *
 * we want to be able to specify combinations, stuff has order, ie facing/tiled before animation
 *  tint?
 *  facing 		-> animation 	-> diffuse
 *  facing 							-> diffuse
 *  tiled 		-> animation 	-> diffuse
 *  tiled 							-> diffuse
 *  animation 						-> diffuse
 *  										diffuse
 *
 *  facing -> spine
 *  tiled -> spine
 *  spine
 *
 *  renderable
 *  	preprocessors?
 * 	asset (diffuse/spine)
 *    shader?
 *
 *  facing - pick stuff based on some state
 *  	animation - pick stuff based on some state
 *  		diffuse
 *  		diffuse
 *  	animation
 *  		diffuse
 *  		diffuse
 *
 *  facing
 *  	diffuse
 *  	diffuse
 *
 *  {
 *     diffuse: {
 *       // some extra offsets or whatever
 *      	path: entities:rwelp
 *     }
 *  }
 *  {
 *  	// all regions same size
 *    facing: [
 *    	{
 *    	 	min:0.max:180
 *    	   diffuse: {
 *    	     	path: entities:path
 *    	   }
 *    	},{
 *				// ...
 *    	}
 *    ]
 *  }
 *  {
 *     // all regions same size
 *     facing: [
 *    	{
 *    	 	min:0.max:180
 *    	   animation: {
	*    		 	frame: 0.16
	*    		 	// automatic animation from texture atlas with given name, atlas.findRegions(name)
	*    		  	path: entities:awelp_f1
	*    		 }
 *    	},{
 *				// ...
 *    	}
 *    ]
 *  }
 *  {
 *  	 // all regions same size
 *     animation: {
 *     	frame: 0.16
 *     	// automatic animation from texture atlas with given name, atlas.findRegions(name)
 *      	path: entities:awelp
 *     }
 *  }
 *  {
 *     diffuse: {
*    	    path: entities:rath
*    	 }
 *  }
 */
public class MyGdxGame extends ApplicationAdapter {

	public static class Asset extends Component {

	}

	public static class Facing extends Component {
		public Array<Direction> facings = new Array<Direction>();
		public float angle;

		public static class Direction {
			public float min;
			public float max;
			public Asset asset;
		}
	}

	public static class Tint extends Component {
		public Color color = new Color(Color.WHITE);
	}

	public static class Animation extends Asset {
		public com.badlogic.gdx.graphics.g2d.Animation<Region> animation;
		public float stateTime;
		public boolean loop = true;

		public Asset init (com.badlogic.gdx.graphics.g2d.Animation<Region> animation) {
			this.animation = animation;
			return this;
		}
	}

	public static class Region extends Asset {
		public TextureRegion diffuse;
		public TextureRegion normal;

		public Region init (TextureRegion region) {
			this.diffuse = region;
			return this;
		}
	}

	public static class Spine extends Asset {
		// TODO
	}



	SpriteBatch batch;
	World world;
	protected ComponentMapper<Renderable> mRenderable;
	protected ComponentMapper<Animation> mAnimation;
	protected ComponentMapper<Facing> mFacing;
	protected ComponentMapper<Region> mRegion;
	TextureAtlas atlas;
	@Override
	public void create () {
		batch = new SpriteBatch();
		atlas = new TextureAtlas(Gdx.files.internal("chars.atlas"));

		WorldConfiguration config = new WorldConfiguration();
		config.register(batch);

		// order of the addition matters, not great
		// facing updater
		config.setSystem(new IteratingSystem(Aspect.all(Facing.class)) {
			protected ComponentMapper<Facing> mFacing;
			@Override protected void process (int entityId) {
				Facing facing = mFacing.get(entityId);
				// TODO angle from some proper place
				facing.angle += world.delta * 90;
				if (facing.angle > 360) facing.angle -= 360;
				// TODO check if angle changed, with threshold?
				for (Facing.Direction direction : facing.facings) {
					if (direction.min <= facing.angle && direction.max > facing.angle) {
						ArtemisUtils.setComponent(entityId, direction.asset);
					}
				}
			}
		});
		// animation updater
		config.setSystem(new IteratingSystem(Aspect.all(Animation.class)) {
			protected ComponentMapper<Animation> mAnimation;
			@Override protected void process (int entityId) {
				Animation animation = mAnimation.get(entityId);
				animation.stateTime += world.delta;
				ArtemisUtils.setComponent(entityId, animation.animation.getKeyFrame(animation.stateTime, animation.loop));
			}
		});
		Renderer renderer = new Renderer();
		config.setSystem(renderer);

		world = new World(config);
		world.inject(this, false);
		ArtemisUtils.world = world;

		renderer.addSubRenderer(new RegionRenderer());

		{
			int entityId = world.create();
			Renderable renderable = mRenderable.create(entityId);
			renderable.x = 0;
			renderable.y = 0;
			renderable.type = Renderable.TYPE_REGION;
			mRegion.create(entityId).diffuse = new TextureRegion(new Texture("badlogic.jpg"));
		}

		{
			int entityId = world.create();
			Renderable renderable = mRenderable.create(entityId);
			renderable.x = 0;
			renderable.y = 300;
			renderable.type = Renderable.TYPE_REGION;
			Facing facing = mFacing.create(entityId);
			Facing.Direction dir1 = new Facing.Direction();
			facing.facings.add(dir1);
			dir1.min = 0;
			dir1.max = 180;
			dir1.asset = new Region().init(new TextureRegion(new Texture("badlogic.jpg")));

			Facing.Direction dir2 = new Facing.Direction();
			facing.facings.add(dir2);
			dir2.min = 180;
			dir2.max = 360;
			dir2.asset = new Region().init(new TextureRegion(new Texture("badlogic.jpg")));
			((Region)dir2.asset).diffuse.flip(true, true);
		}
		{
			int entityId = world.create();
			Renderable renderable = mRenderable.create(entityId);
			renderable.x = 300;
			renderable.y = 200;
			renderable.type = Renderable.TYPE_REGION;
			Animation animation = mAnimation.create(entityId);
			animation.stateTime = 0;
			Array<TextureAtlas.AtlasRegion> atlasRegions = atlas.findRegions("char_bat/0");
			Array<Region> regions = new Array<Region>();
			for (TextureAtlas.AtlasRegion atlasRegion : atlasRegions) {
				regions.add(new Region().init(atlasRegion));
			}

			animation.animation = new com.badlogic.gdx.graphics.g2d.Animation<Region>(1/20f, regions);
		}
		{
			int entityId = world.create();
			Renderable renderable = mRenderable.create(entityId);
			renderable.x = 300;
			renderable.y = 300;
			renderable.type = Renderable.TYPE_REGION;
			Facing facing = mFacing.create(entityId);
			Facing.Direction dir1 = new Facing.Direction();
			facing.facings.add(dir1);
			dir1.min = 0;
			dir1.max = 180;
			{
				Array<TextureAtlas.AtlasRegion> atlasRegions = atlas.findRegions("char_bat/90");
				Array<Region> regions = new Array<Region>();
				for (TextureAtlas.AtlasRegion atlasRegion : atlasRegions) {
					regions.add(new Region().init(atlasRegion));
				}

				dir1.asset = new Animation().init(new com.badlogic.gdx.graphics.g2d.Animation<Region>(1/20f, regions));
			}

			Facing.Direction dir2 = new Facing.Direction();
			facing.facings.add(dir2);
			dir2.min = 180;
			dir2.max = 360;
			{
				Array<TextureAtlas.AtlasRegion> atlasRegions = atlas.findRegions("char_bat/270");
				Array<Region> regions = new Array<Region>();
				for (TextureAtlas.AtlasRegion atlasRegion : atlasRegions) {
					regions.add(new Region().init(atlasRegion));
				}

				dir2.asset = new Animation().init(new com.badlogic.gdx.graphics.g2d.Animation<Region>(1/20f, regions));
			}
		}
	}

	@Override
	public void render () {
		Gdx.gl.glClearColor(1, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		world.delta = Gdx.graphics.getDeltaTime();
		world.process();
	}

	public static class Renderable extends Component {
		public final static int TYPE_REGION = 0;
		public final static int TYPE_SPINE = 1;
		public float x;
		public float y;
		public int type;
	}

	public static class Renderer extends BaseEntitySystem {
		private static final String TAG = Renderer.class.getSimpleName();
		protected ComponentMapper<Renderable> mRenderable;
		protected int nextId;
		protected SubRenderer[] renderers;

		public Renderer () {
			super(Aspect.all(Renderable.class));
			renderers = new SubRenderer[16];
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
	}

	public static class RegionRenderer extends Renderer.SubRenderer {
		private static final String TAG = RegionRenderer.class.getSimpleName();
		@Wire SpriteBatch batch;
		protected ComponentMapper<Renderable> mRenderable;
		protected ComponentMapper<Region> mRegion;
		@Override public void begin () {
			batch.begin();
		}

		@Override public void render (int entityId) {
			Renderable renderable = mRenderable.get(entityId);
			Region rc = mRegion.get(entityId);
			if (rc == null) return;
			TextureRegion region = rc.diffuse;
			Gdx.app.log(TAG, "Render diffuse " + entityId);

			batch.draw(region, renderable.x, renderable.y, region.getRegionWidth() * .5f, region.getRegionHeight() * .5f);
		}

		@Override public void end () {
			batch.end();
		}
	}

	@Override
	public void dispose () {
		world.dispose();
		batch.dispose();
	}
}
