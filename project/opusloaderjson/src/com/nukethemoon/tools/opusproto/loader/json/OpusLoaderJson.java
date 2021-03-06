package com.nukethemoon.tools.opusproto.loader.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nukethemoon.tools.opusproto.Config;
import com.nukethemoon.tools.opusproto.interpreter.TypeInterpreter;
import com.nukethemoon.tools.opusproto.interpreter.ColorInterpreter;
import com.nukethemoon.tools.opusproto.layer.LayerConfig;
import com.nukethemoon.tools.opusproto.sampler.AbstractSampler;
import com.nukethemoon.tools.opusproto.sampler.Samplers;
import com.nukethemoon.tools.opusproto.exceptions.SamplerInvalidConfigException;
import com.nukethemoon.tools.opusproto.exceptions.SamplerRecursionException;
import com.nukethemoon.tools.opusproto.exceptions.SamplerUnresolvedDependencyException;
import com.nukethemoon.tools.opusproto.generator.OpusConfiguration;
import com.nukethemoon.tools.opusproto.generator.Opus;
import com.nukethemoon.tools.opusproto.layer.Layer;
import com.nukethemoon.tools.opusproto.noise.Algorithms;
import com.nukethemoon.tools.opusproto.sampler.AbstractSamplerConfiguration;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * This class loads and saves Opus using a json file.
 */
public class OpusLoaderJson {

	private Gson gson;
	private static Charset CHARSET = StandardCharsets.UTF_8;

	/**
	 * Creates a new instance.
	 */
	public OpusLoaderJson() {
		gson = new GsonBuilder().setPrettyPrinting().create();
	}

	/**
	 * Loads Opus by a json file located
	 * at the assigned relative path.
	 * @param filePath The relative path to the json file.
	 * @return The initialized WorldGenerator.
	 * @throws SamplerInvalidConfigException
	 * @throws SamplerUnresolvedDependencyException
	 * @throws SamplerRecursionException
	 * @throws IOException
	 */
	public Opus load(String filePath) throws SamplerInvalidConfigException, SamplerUnresolvedDependencyException,
			SamplerRecursionException, IOException {
		return load(new Samplers(), new Algorithms(), readFile(filePath));
	}

	/**
	 * Loads Opus by a json file located
	 * at the assigned relative path. An instance of SamplerLoader
	 * and NoiseAlgorithmPool can be passed if they
	 * are initialized before.
	 * @param samplers The Samplers to use.
	 * @param algorithms The Algorithms to use.
	 * @param bytes The content of a json file (utf8) in bytes.
	 * @return The initialized WorldGenerator.
	 * @throws SamplerInvalidConfigException
	 * @throws SamplerUnresolvedDependencyException
	 * @throws SamplerRecursionException
	 * @throws IOException
	 */
	public Opus load(Samplers samplers, Algorithms algorithms, byte[] bytes)
			throws SamplerInvalidConfigException, SamplerUnresolvedDependencyException,
			SamplerRecursionException, IOException {

		samplers = samplers == null ? new Samplers() : samplers;
		algorithms = algorithms == null ? new Algorithms() : algorithms;

		PersistenceOpus persistence = gson.fromJson(new String(bytes, CHARSET), PersistenceOpus.class);
		OpusConfiguration opusConfiguration = persistence.worldConfig;
		opusConfiguration.seed = Double.parseDouble(opusConfiguration.seedString);

		AbstractSamplerConfiguration[] samplerConfigs = new AbstractSamplerConfiguration[persistence.samplerConfigs.length];
		for (int samplerIndex = 0; samplerIndex < persistence.samplerConfigs.length; samplerIndex++) {
			PersistenceOpus.SamplerConfigEntry entry = persistence.samplerConfigs[samplerIndex];
			samplerConfigs[samplerIndex] = gson.fromJson(entry.data, Samplers.getConfigClassByName(entry.type));
		}
		samplers.loadSamplers(samplerConfigs, opusConfiguration.seed, algorithms);

		for (int interpreterIndex = 0; interpreterIndex < persistence.interpreters.length; interpreterIndex++) {
			samplers.addInterpreter(persistence.interpreters[interpreterIndex]);
		}

		Layer[] layerList = new Layer[persistence.layerConfigs.length];
		for (int layerIndex = 0; layerIndex < persistence.layerConfigs.length; layerIndex++) {
			layerList[layerIndex] = new Layer(persistence.layerConfigs[layerIndex], opusConfiguration.seed, samplers);
		}
		return new Opus(opusConfiguration, layerList);
	}

	public byte[] createBytes(Samplers samplers, Opus opus) throws IOException {
		PersistenceOpus save = new PersistenceOpus();
		TypeInterpreter[] interpreterList = samplers.createInterpreterList();
		save.interpreters = new ColorInterpreter[interpreterList.length];
		for (int i = 0; i < interpreterList.length; i++) {
			save.interpreters[i] = (ColorInterpreter) interpreterList[i];
		}

		AbstractSampler[] samplerList = samplers.createSamplerList();
		save.samplerConfigs = new PersistenceOpus.SamplerConfigEntry[samplerList.length];
		for (int samplerIndex = 0; samplerIndex < samplerList.length; samplerIndex++) {
			save.samplerConfigs[samplerIndex] = new PersistenceOpus.SamplerConfigEntry(
					samplerList[samplerIndex].getConfig().getClass().getSimpleName(),
					gson.toJsonTree(samplerList[samplerIndex].getConfig()));
		}


		Layer[] layerList = opus.getLayers().toArray(new Layer[opus.getLayers().size()]);
		save.layerConfigs = new LayerConfig[layerList.length];
		for (int layerIndex = 0; layerIndex < layerList.length; layerIndex++) {
			save.layerConfigs[layerIndex] = (LayerConfig) layerList[layerIndex].getConfig();
		}

		OpusConfiguration opusConfig = opus.getConfig();
		String[] layerIds = new String[opus.getLayers().size()];
		for (int i = 0; i < layerIds.length; i++) {
			layerIds[i] = opus.getLayers().get(i).getConfig().id;
		}
		opusConfig.seedString = opusConfig.seed + "";
		opusConfig.layerIds = layerIds;
		save.worldConfig = opusConfig;
		save.version = Config.VERSION;

		String saveJson = gson.toJson(save, PersistenceOpus.class);
		return saveJson.getBytes(CHARSET);
	}

	/**
	 * Saves Opus to a json file located at the assigned path.
	 * @param samplers The Samplers to save.
	 * @param opus The Opus to save.
	 * @param saveFilePath The file path to save to.
	 */
	public void save(Samplers samplers, Opus opus, String saveFilePath) throws IOException {
		File file = new File(saveFilePath);
		if (!file.exists()) {
			file.createNewFile();
		}
		writeFile(file, createBytes(samplers, opus));
	}

	// == file helper functions ==
	public void writeFile(File file, byte[] bytes) throws IOException {
		FileOutputStream fs = new FileOutputStream(file);
		BufferedOutputStream bs = new BufferedOutputStream(fs);
		bs.write(bytes);
		bs.close();
	}

	public byte[] readFile(String filePath) throws IOException {
		File file = new File(filePath);
		return readFile(file);
	}

	public byte[] readFile(File file) throws IOException {
		byte[] buffer = new byte[(int) file.length()];
		InputStream ios = null;
		try {
			ios = new FileInputStream(file);
			if (ios.read(buffer) == -1) {
				throw new IOException(
						"EOF reached while trying to read the whole file");
			}
		} finally {
			try {
				if (ios != null)
					ios.close();
			} catch (IOException e) {
			}
		}
		return buffer;
	}
}
