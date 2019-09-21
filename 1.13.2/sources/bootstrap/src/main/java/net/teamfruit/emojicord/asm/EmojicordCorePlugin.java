package net.teamfruit.emojicord.asm;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import org.objectweb.asm.tree.ClassNode;

import com.google.common.collect.Lists;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.INameMappingService.Domain;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import net.teamfruit.emojicord.Reference;

public class EmojicordCorePlugin implements ITransformationService {
	public static @Nullable BiFunction<Domain, String, String> Srg2Mcp;
	public static Set<String> TransformerServices;

	@Override
	public void onLoad(final IEnvironment env, final Set<String> otherServices) throws IncompatibleEnvironmentException {
		TransformerServices = otherServices;
	}

	@Override
	public void initialize(final IEnvironment environment) {
	}

	@Override
	public void beginScanning(final IEnvironment environment) {
		Srg2Mcp = environment.findNameMapping("srg").orElse(null);
		// Load mod manually when product environment.
		if (Srg2Mcp==null)
			new EmojicordFMLPlugin().discoverMods();
	}

	@Override
	public String name() {
		return getClass().getSimpleName();
	}

	// Decorator for Forge detecting interface generic type
	public static class TransformerDecorator implements ITransformer<ClassNode> {
		private final ITransformer<ClassNode> transformer;

		public TransformerDecorator(final ITransformer<ClassNode> decoratee) {
			this.transformer = decoratee;
		}

		@Override
		public ClassNode transform(final ClassNode input, final ITransformerVotingContext context) {
			return this.transformer.transform(input, context);
		}

		@Override
		public TransformerVoteResult castVote(final ITransformerVotingContext context) {
			return this.transformer.castVote(context);
		}

		@Override
		public Set<Target> targets() {
			return this.transformer.targets();
		}
	}

	@SuppressWarnings("rawtypes")
	@Override
	public List<ITransformer> transformers() {
		try {
			@SuppressWarnings("unchecked")
			final ITransformer<ClassNode> transformer = (ITransformer<ClassNode>) Class.forName(Reference.TRANSFORMER).newInstance();
			return Lists.newArrayList(new TransformerDecorator(transformer));
		} catch (InstantiationException|IllegalAccessException|ClassNotFoundException e) {
			throw new RuntimeException("Failed to load transformer", e);
		}
	}
}