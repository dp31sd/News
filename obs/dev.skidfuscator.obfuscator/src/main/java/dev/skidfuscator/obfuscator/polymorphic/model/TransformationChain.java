package dev.skidfuscator.obfuscator.polymorphic.model;

import dev.skidfuscator.obfuscator.polymorphic.transforms.Permutation;
import dev.skidfuscator.obfuscator.polymorphic.transforms.model.Transformation;

import java.util.ArrayList;
import java.util.function.Function;

public class TransformationChain extends ArrayList<Transformation> implements Function<Long, Long> {

	@Override
	public Long apply(Long t) {
		long c = t;
		for (int i=0; i<size(); i++)
			c = get(i).transform(c);
		return c;
	}
	
	public TransformationChain reverse() {
		TransformationChain reverse = new TransformationChain();
		for (int i=size() - 1; i >= 0; --i)
			reverse.add(get(i).reversed());
		return reverse;
	}
	
	public <T extends Transformation> boolean contains(Class<T> cls) {
		for (Transformation t : this)
			if (cls.isInstance(t))
				return true;
		return false;
	}
	
	public boolean containsPermutation() {
		return contains(Permutation.class);
	}
}
