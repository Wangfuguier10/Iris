package ninja.bytecode.iris.generator;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.World;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.layer.GenLayerBiome;
import ninja.bytecode.iris.object.InferredType;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.object.IrisBiomeGeneratorLink;
import ninja.bytecode.iris.object.IrisDimension;
import ninja.bytecode.iris.object.IrisGenerator;
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.util.BiomeResult;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.ChronoLatch;
import ninja.bytecode.iris.util.ChunkPosition;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;
import ninja.bytecode.shuriken.math.M;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class BiomeChunkGenerator extends DimensionChunkGenerator
{
	protected ReentrantLock regLock;
	private KMap<String, IrisGenerator> generators;
	private KMap<String, IrisGenerator> ceilingGenerators;
	protected GenLayerBiome glBiome;
	protected CNG masterFracture;
	private KMap<ChunkPosition, BiomeResult> biomeHitCache;
	private KMap<ChunkPosition, BiomeResult> ceilingBiomeHitCache;
	protected ChronoLatch cwarn = new ChronoLatch(1000);

	public BiomeChunkGenerator(String dimensionName)
	{
		super(dimensionName);
		generators = new KMap<>();
		ceilingGenerators = new KMap<>();
		regLock = new ReentrantLock();
		biomeHitCache = new KMap<>();
		ceilingBiomeHitCache = new KMap<>();
	}

	public void onInit(World world, RNG rng)
	{
		loadGenerators();
		glBiome = new GenLayerBiome(this, masterRandom.nextParallelRNG(1));
		masterFracture = CNG.signature(rng.nextParallelRNG(13)).scale(0.12);
	}

	public KMap<ChunkPosition, BiomeResult> getBiomeHitCache()
	{
		return getDimension().isInverted() ? ceilingBiomeHitCache : biomeHitCache;
	}

	@Override
	public void onHotloaded()
	{
		super.onHotloaded();
		biomeHitCache = new KMap<>();
		ceilingBiomeHitCache = new KMap<>();
		loadGenerators();
	}

	public void registerGenerator(IrisGenerator g, IrisDimension dim)
	{
		KMap<String, IrisGenerator> generators = dim.isInverted() ? ceilingGenerators : this.generators;

		regLock.lock();
		if(g.getLoadKey() == null || generators.containsKey(g.getLoadKey()))
		{
			regLock.unlock();
			return;
		}

		regLock.unlock();
		generators.put(g.getLoadKey(), g);
	}

	protected KMap<String, IrisGenerator> getGenerators()
	{
		return getDimension().isInverted() ? ceilingGenerators : generators;
	}

	protected double getBiomeHeight(double rx, double rz)
	{
		double h = 0;

		for(IrisGenerator i : getGenerators().values())
		{
			h += interpolateGenerator(rx, rz, i);
		}

		return h;
	}

	protected double interpolateGenerator(double rx, double rz, IrisGenerator gen)
	{
		double hi = IrisInterpolation.getNoise(gen.getInterpolationFunction(), (int) Math.round(rx), (int) Math.round(rz), gen.getInterpolationScale(), (xx, zz) ->
		{
			IrisBiome b = sampleBiome((int) xx, (int) zz).getBiome();

			for(IrisBiomeGeneratorLink i : b.getGenerators())
			{
				if(i.getGenerator().equals(gen.getLoadKey()))
				{
					return i.getMax();
				}
			}

			return 0;
		});

		double lo = IrisInterpolation.getNoise(gen.getInterpolationFunction(), (int) Math.round(rx), (int) Math.round(rz), gen.getInterpolationScale(), (xx, zz) ->
		{
			IrisBiome b = sampleBiome((int) xx, (int) zz).getBiome();

			for(IrisBiomeGeneratorLink i : b.getGenerators())
			{
				if(i.getGenerator().equals(gen.getLoadKey()))
				{
					return i.getMin();
				}
			}

			return 0;
		});

		return M.lerp(lo, hi, gen.getHeight(rx, rz, world.getSeed() + 239945));
	}

	protected void loadGenerators()
	{
		generators.clear();
		ceilingGenerators.clear();
		loadGenerators(((CeilingChunkGenerator) this).getFloorDimension());
		loadGenerators(((CeilingChunkGenerator) this).getCeilingDimension());
	}

	protected void loadGenerators(IrisDimension dim)
	{
		if(dim == null)
		{
			return;
		}

		KList<String> touch = new KList<>();
		KList<String> loadQueue = new KList<>();

		for(String i : dim.getRegions())
		{
			IrisRegion r = Iris.data.getRegionLoader().load(i);

			if(r != null)
			{
				loadQueue.addAll(r.getLandBiomes());
				loadQueue.addAll(r.getSeaBiomes());
				loadQueue.addAll(r.getShoreBiomes());
			}
		}

		while(!loadQueue.isEmpty())
		{
			String next = loadQueue.pop();

			if(!touch.contains(next))
			{
				touch.add(next);
				IrisBiome biome = Iris.data.getBiomeLoader().load(next);
				biome.getGenerators().forEach((i) -> registerGenerator(i.getCachedGenerator(), dim));
				loadQueue.addAll(biome.getChildren());
			}
		}
	}

	public IrisRegion sampleRegion(int x, int z)
	{
		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		return glBiome.getRegion(wx, wz);
	}

	public BiomeResult sampleBiome(int x, int z)
	{
		if(!getDimension().getFocus().equals(""))
		{
			IrisBiome biome = Iris.data.getBiomeLoader().load(getDimension().getFocus());

			for(String i : getDimension().getRegions())
			{
				IrisRegion reg = Iris.data.getRegionLoader().load(i);

				if(reg.getLandBiomes().contains(biome.getLoadKey()))
				{
					biome.setInferredType(InferredType.LAND);
					break;
				}

				if(reg.getSeaBiomes().contains(biome.getLoadKey()))
				{
					biome.setInferredType(InferredType.SEA);
					break;
				}

				if(reg.getShoreBiomes().contains(biome.getLoadKey()))
				{
					biome.setInferredType(InferredType.SHORE);
					break;
				}
			}

			return new BiomeResult(biome, 0);
		}

		ChunkPosition pos = new ChunkPosition(x, z);

		if(getBiomeHitCache().containsKey(pos))
		{
			return getBiomeHitCache().get(pos);
		}

		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		IrisRegion region = glBiome.getRegion(wx, wz);
		BiomeResult res = glBiome.generateRegionData(wx, wz, x, z, region);
		getBiomeHitCache().put(pos, res);

		return res;
	}
}