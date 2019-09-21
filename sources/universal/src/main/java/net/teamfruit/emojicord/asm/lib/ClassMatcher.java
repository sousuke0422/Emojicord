/*
 * This class is from the OpenModsLib.
 * https://github.com/OpenMods/OpenModsLib
 *
 * Code Copyright (c) 2013 Open Mods
 * Code released under the MIT license
 * https://github.com/OpenMods/OpenModsLib/blob/master/LICENSE
 */
package net.teamfruit.emojicord.asm.lib;

import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.objectweb.asm.tree.ClassNode;

import net.teamfruit.emojicord.compat.CompatFMLDeobfuscatingRemapper;

public class ClassMatcher implements Predicate<ClassNode> {
	private final @Nonnull ClassName clsName;
	private final @Nonnull String unmappedClassName;

	public ClassMatcher(final @Nonnull ClassName clsName) {
		this.clsName = clsName;
		this.unmappedClassName = CompatFMLDeobfuscatingRemapper.unmap(this.clsName.getBytecodeName());
	}

	public boolean match(final @Nonnull String className) {
		return this.unmappedClassName.equals(className);
	}

	@Override
	public boolean test(final ClassNode node) {
		return match(node.name);
	}

	@Override
	public @Nonnull String toString() {
		return String.format("Class Matcher: %s", this.clsName.getBytecodeName());
	}
}