package net.teamfruit.emojicord.compat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.objectweb.asm.tree.ClassNode;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;

public abstract class CompatTransformer implements ITransformer<ClassNode> {
	public abstract ClassNode transform(final ClassNode input, final CompatTransformerVotingContext context);

	public abstract DeferredTransform[] deferredTransforms();

	public abstract String[] targetNames();

	public static class CompatTransformerVotingContext {
	}

	public static class DeferredTransform {
		private final String targetname;

		public DeferredTransform(final String thisname, final String targetname) {
			this.targetname = targetname;
		}

		public boolean shouldDefer() {
			try {
				Class.forName(this.targetname, false, getClass().getClassLoader());
				return true;
			} catch (final ClassNotFoundException e) {
			}
			return false;
		}
	}

	@Override
	public TransformerVoteResult castVote(final ITransformerVotingContext context) {
		return Arrays.stream(deferredTransforms()).anyMatch(DeferredTransform::shouldDefer) ? TransformerVoteResult.DEFER : TransformerVoteResult.YES;
	}

	@Override
	public Set<Target> targets() {
		return Arrays.stream(targetNames()).map(Target::targetClass).collect(Collectors.toSet());
	}

	@Override
	public ClassNode transform(final ClassNode input, final ITransformerVotingContext context) {
		return transform(input, new CompatTransformerVotingContext());
	}
}
